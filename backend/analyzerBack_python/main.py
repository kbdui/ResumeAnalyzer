from __future__ import annotations

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
    resume_id: Optional[str] = None
    file_name: Optional[str] = None
    text: str = ""
    skills: List[str] = Field(default_factory=list)
    work_experience: List[Any] = Field(default_factory=list)
    projects: List[Any] = Field(default_factory=list)


class MatchPipelineRequest(BaseModel):
    jd_text: str
    resumes: List[ResumeDocument]
    top_k: int = 20
    recall_k: int = 200


class TfidfRequest(BaseModel):
    resume_data: Dict[str, Any]
    job_description: str


def _now() -> float:
    return time.time()


def _get_embedder():
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
    jd_terms = _extract_keywords(jd_text)
    if not jd_terms:
        return 0.0
    resume_skill_text = " ".join(resume.skills or [])
    pool_text = f"{resume_skill_text} {resume.text or ''}"
    matched = sum(1 for term in jd_terms if term in pool_text)
    return matched / len(jd_terms)


def _recall_stage(req: MatchPipelineRequest) -> List[Dict[str, Any]]:
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
    rows = []
    for item in parts or []:
        if isinstance(item, dict):
            rows.append(" ".join([str(v) for v in item.values() if v is not None]))
        else:
            rows.append(str(item))
    return " ".join(rows)


def _embed_score(resume: Dict[str, Any], jd_text: str) -> float:
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
    return calculate_tfidf_similarity(req.resume_data, req.job_description)


@app.post("/match/pipeline")
def match_pipeline(req: MatchPipelineRequest):
    return run_pipeline(req)


@app.post("/tasks/match-pipeline")
def create_match_task(req: MatchPipelineRequest):
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
