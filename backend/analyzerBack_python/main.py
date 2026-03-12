from __future__ import annotations

"""
FastAPI 简历匹配服务（Python 侧）。

提供两类能力：
- 同步匹配：TF-IDF 以及 “召回 + 重排” 的 pipeline；
- 异步匹配：将 pipeline 任务放入内存队列，后台线程执行，支持查询任务状态与结果。

说明：
- 异步任务状态与结果存放于进程内内存（TASK_STORE），服务重启后会丢失。
- embedding 重排依赖 sentence-transformers；未安装时会自动回退到 TF-IDF 分数以保证可用性。
"""

import queue
import threading
import time
import uuid
from typing import Any, Dict, List, Optional

import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel, Field

from resumeAnalyze import calculate_tfidf_similarity, tfidf_cosine, tokenize_mixed

try:
    from sentence_transformers import SentenceTransformer
except Exception:
    SentenceTransformer = None

app = FastAPI(title="Resume Matching Service")

EMBEDDING_MODEL_NAME = "BAAI/bge-small-zh-v1.5"
_embedder = None
_embedder_error = None

TASK_QUEUE: queue.Queue[str] = queue.Queue()
TASK_STORE: Dict[str, Dict[str, Any]] = {}
TASK_LOCK = threading.Lock()


class ResumeDocument(BaseModel):
    """单份简历的标准化输入结构（pipeline 输入的一部分）。"""

    resume_id: Optional[str] = None
    file_name: Optional[str] = None
    text: str = ""
    skills: List[str] = Field(default_factory=list)
    work_experience: List[Any] = Field(default_factory=list)
    projects: List[Any] = Field(default_factory=list)


class MatchPipelineRequest(BaseModel):
    """匹配 pipeline 的请求体：JD 文本 + 多份简历 + TopK/RecallK 控制参数。"""

    jd_text: str
    resumes: List[ResumeDocument]
    top_k: int = 20
    recall_k: int = 200


class TfidfRequest(BaseModel):
    """兼容历史的 TF-IDF 接口请求体（单简历数据 + JD）。"""

    resume_data: Dict[str, Any]
    job_description: str


def _now() -> float:
    """返回当前时间戳（秒）。用于记录任务 created/started/ended。"""
    return time.time()


def _get_embedder():
    """
    延迟加载 embedding 模型（sentence-transformers）。

    返回：
    - SentenceTransformer 实例（可用时）
    - None（未安装依赖或初始化失败时；同时记录错误信息用于对外暴露）
    """
    global _embedder, _embedder_error
    if _embedder is not None:
        return _embedder
    if _embedder_error is not None:
        return None
    if SentenceTransformer is None:
        _embedder_error = "sentence-transformers not installed"
        return None
    try:
        _embedder = SentenceTransformer(EMBEDDING_MODEL_NAME)
        return _embedder
    except Exception as e:
        _embedder_error = str(e)
        return None


def _extract_keywords(text: str, max_terms: int = 40) -> List[str]:
    """
    从输入文本中抽取用于“技能覆盖率”计算的关键词集合。

    这里复用 tokenize_mixed 的分词结果，按出现顺序去重并截断到 max_terms。
    """
    token_str = tokenize_mixed(text)
    tokens = [t for t in token_str.split(" ") if len(t) > 1]
    unique = []
    seen = set()
    for t in tokens:
        if t not in seen:
            seen.add(t)
            unique.append(t)
        if len(unique) >= max_terms:
            break
    return unique


def _skill_coverage(resume: ResumeDocument, jd_text: str) -> float:
    """
    估算简历对 JD 的“关键词覆盖率”。

    做法：从 JD 抽取关键词集合，统计这些关键词在 (skills + raw text) 中出现的比例。
    """
    jd_terms = _extract_keywords(jd_text)
    if not jd_terms:
        return 0.0
    resume_skill_text = " ".join(resume.skills or [])
    pool_text = f"{resume_skill_text} {resume.text or ''}"
    matched = sum(1 for term in jd_terms if term in pool_text)
    return matched / len(jd_terms)


def _recall_stage(req: MatchPipelineRequest) -> List[Dict[str, Any]]:
    """
    召回阶段：对每份简历计算 tfidf + 覆盖率的粗排分，过滤明显不相关项并取前 recall_k。

    输出为候选列表（dict），会带上召回分及可解释字段（top_terms 等）。
    """
    candidates: List[Dict[str, Any]] = []
    for idx, resume in enumerate(req.resumes):
        tfidf_score, top_terms = tfidf_cosine(resume.text, req.jd_text)
        coverage = _skill_coverage(resume, req.jd_text)
        recall_score = 0.8 * tfidf_score + 0.2 * coverage

        # 过滤明显不相关
        if tfidf_score < 0.03 and coverage < 0.05:
            continue

        candidates.append(
            {
                "resume_id": resume.resume_id or str(idx),
                "file_name": resume.file_name or "",
                "text": resume.text,
                "skills": resume.skills,
                "work_experience": resume.work_experience,
                "projects": resume.projects,
                "recall_score": float(recall_score),
                "tfidf_score": float(tfidf_score),
                "skill_coverage": float(coverage),
                "top_terms": top_terms,
            }
        )
    candidates.sort(key=lambda x: x["recall_score"], reverse=True)
    return candidates[: max(1, req.recall_k)]


def _field_text(parts: List[Any]) -> str:
    """
    将结构化字段（工作经历/项目等）拼接为可用于 embedding 的文本。

    - dict：拼接其 values
    - 其他：直接 str()
    """
    rows = []
    for item in parts or []:
        if isinstance(item, dict):
            rows.append(" ".join([str(v) for v in item.values() if v is not None]))
        else:
            rows.append(str(item))
    return " ".join(rows)


def _embed_score(resume: Dict[str, Any], jd_text: str) -> float:
    """
    重排阶段的 embedding 相似度打分。

    - 可用时：计算 skills/work/projects/raw 与 JD 的向量相似度加权和
    - 不可用时：回退到召回阶段的 tfidf_score，确保 pipeline 可运行
    """
    embedder = _get_embedder()
    if embedder is None:
        # fallback: 未安装 embedding 依赖时使用 tfidf 分替代，保证流程可跑
        return float(resume.get("tfidf_score", 0.0))

    skills_text = " ".join(resume.get("skills") or [])
    work_text = _field_text(resume.get("work_experience") or [])
    project_text = _field_text(resume.get("projects") or [])
    raw_text = resume.get("text") or ""

    def sim(a: str, b: str) -> float:
        if not a.strip() or not b.strip():
            return 0.0
        vec = embedder.encode([a, b], normalize_embeddings=True)
        return float(np.dot(vec[0], vec[1]))

    s_skills = sim(skills_text, jd_text)
    s_work = sim(work_text, jd_text)
    s_proj = sim(project_text, jd_text)
    s_raw = sim(raw_text, jd_text)

    # 你确认的初始权重
    return float(0.4 * s_skills + 0.3 * s_work + 0.2 * s_proj + 0.1 * s_raw)


def _rerank_stage(candidates: List[Dict[str, Any]], jd_text: str, top_k: int) -> Dict[str, Any]:
    """
    重排阶段：对召回候选进行 embedding 打分，并与 recall_score 融合得到最终排序。

    返回结构包含模型信息、是否 fallback、错误信息（若有）以及 top_k 条结果。
    """
    results = []
    for c in candidates:
        emb_score = _embed_score(c, jd_text)
        final_score = 0.65 * emb_score + 0.35 * c["recall_score"]
        c["embedding_score"] = float(emb_score)
        c["final_score"] = float(final_score)
        results.append(c)
    results.sort(key=lambda x: x["final_score"], reverse=True)
    return {
        "model": EMBEDDING_MODEL_NAME,
        "embedding_fallback": _embedder is None,
        "embedding_error": _embedder_error,
        "items": results[: max(1, top_k)],
    }


def run_pipeline(req: MatchPipelineRequest) -> Dict[str, Any]:
    """
    执行完整匹配 pipeline（同步）。

    流程：recall -> rerank，并返回 summary + results。
    """
    start = _now()
    recall_candidates = _recall_stage(req)
    rerank = _rerank_stage(recall_candidates, req.jd_text, req.top_k)
    return {
        "summary": {
            "total_resumes": len(req.resumes),
            "recall_count": len(recall_candidates),
            "top_k": req.top_k,
            "elapsed_ms": int((_now() - start) * 1000),
        },
        "results": rerank,
    }


def _worker():
    """
    后台工作线程：从 TASK_QUEUE 取出任务，执行 pipeline，并更新 TASK_STORE 状态与结果。

    状态流转：
    - queued -> running -> done
    - queued/running -> failed（发生异常时）
    """
    while True:
        task_id = TASK_QUEUE.get()
        with TASK_LOCK:
            task = TASK_STORE.get(task_id)
            if task is None:
                TASK_QUEUE.task_done()
                continue
            task["status"] = "running"
            task["started_at"] = _now()
            req_dict = task["request"]
        try:
            req = MatchPipelineRequest.model_validate(req_dict)
            result = run_pipeline(req)
            with TASK_LOCK:
                task["status"] = "done"
                task["result"] = result
                task["ended_at"] = _now()
        except Exception as e:
            with TASK_LOCK:
                task["status"] = "failed"
                task["error"] = str(e)
                task["ended_at"] = _now()
        finally:
            TASK_QUEUE.task_done()


_worker_thread = threading.Thread(target=_worker, daemon=True)
_worker_thread.start()


@app.post("/match/tfidf")
def match_tfidf(req: TfidfRequest):
    """同步 TF-IDF 匹配接口（历史/兼容用）。"""
    return calculate_tfidf_similarity(req.resume_data, req.job_description)


@app.post("/match/pipeline")
def match_pipeline(req: MatchPipelineRequest):
    """同步 pipeline 匹配接口：直接返回召回+重排结果。"""
    return run_pipeline(req)


@app.post("/tasks/match-pipeline")
def create_match_task(req: MatchPipelineRequest):
    """
    创建异步 pipeline 任务。

    返回：
    - task_id：用于后续查询
    - status：初始为 queued
    """
    task_id = str(uuid.uuid4())
    with TASK_LOCK:
        TASK_STORE[task_id] = {
            "task_id": task_id,
            "status": "queued",
            "created_at": _now(),
            "request": req.model_dump(),
        }
    TASK_QUEUE.put(task_id)
    return {"task_id": task_id, "status": "queued"}


@app.get("/tasks/{task_id}")
def get_task(task_id: str):
    """
    查询异步任务状态与结果。

    - not_found：任务不存在（或服务重启后内存丢失）
    - queued/running/done/failed：返回对应时间戳、错误信息与结果（若已完成）
    """
    with TASK_LOCK:
        task = TASK_STORE.get(task_id)
        if task is None:
            return {"task_id": task_id, "status": "not_found"}
        return {
            "task_id": task_id,
            "status": task["status"],
            "created_at": task.get("created_at"),
            "started_at": task.get("started_at"),
            "ended_at": task.get("ended_at"),
            "error": task.get("error"),
            "result": task.get("result"),
        }
