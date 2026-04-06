from __future__ import annotations

import time
from typing import Any, Dict, List

import numpy as np

from models import ResumeDocument
from resumeAnalyze import tfidf_cosine, tokenize_mixed
from schemas import MatchPipelineRequest

try:
    from sentence_transformers import SentenceTransformer
except Exception:
    SentenceTransformer = None

EMBEDDING_MODEL_NAME = "BAAI/bge-small-zh-v1.5"
_embedder = None
_embedder_error = None


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


def _split_keyword_str(keyword_str: str) -> List[str]:
    return [t.strip() for t in (keyword_str or "").split(" ") if t and t.strip()]


def _keyword_coverage_from_terms(resume_text: str, jd_terms: List[str]) -> float:
    if not jd_terms:
        return 0.0
    pool_text = resume_text or ""
    matched = sum(1 for term in jd_terms if term in pool_text)
    return matched / len(jd_terms)


def _keyword_coverage(resume: ResumeDocument, jd_text: str) -> float:
    jd_terms = _extract_keywords(jd_text)
    return _keyword_coverage_from_terms(resume.text or "", jd_terms)


def _recall_stage(req: MatchPipelineRequest) -> List[Dict[str, Any]]:
    candidates: List[Dict[str, Any]] = []
    for idx, resume in enumerate(req.resumes):
        tfidf_score, top_terms = tfidf_cosine(resume.text, req.jd_text)
        coverage = _keyword_coverage(resume, req.jd_text)
        recall_score = 0.8 * tfidf_score + 0.2 * coverage

        if tfidf_score < 0.03 and coverage < 0.05:
            continue

        candidates.append(
            {
                "resume_id": resume.resume_id or str(idx),
                "file_name": resume.file_name or "",
                "text": resume.text,
                "recall_score": float(recall_score),
                "tfidf_score": float(tfidf_score),
                "keyword_coverage": float(coverage),
                "top_terms": top_terms,
                "full_text_score": float(recall_score),
            }
        )
    candidates.sort(key=lambda x: x["recall_score"], reverse=True)
    return candidates[: max(1, req.recall_k)]


def _embed_score(resume: Dict[str, Any], jd_text: str) -> float:
    embedder = _get_embedder()
    if embedder is None:
        return float(resume.get("tfidf_score", 0.0))

    raw_text = resume.get("text") or ""
    if not raw_text.strip() or not jd_text.strip():
        return 0.0
    vec = embedder.encode([raw_text, jd_text], normalize_embeddings=True)
    return float(np.dot(vec[0], vec[1]))


def _rerank_stage(candidates: List[Dict[str, Any]], jd_text: Dict[str, Any], top_k: int) -> Dict[str, Any]:
    # 来自 LLM 的 JD 维度关键词（空格分隔字符串）
    work_terms = _split_keyword_str(jd_text.get("work_experience_keywords") if isinstance(jd_text, dict) else "")
    skills_terms = _split_keyword_str(jd_text.get("skills_keywords") if isinstance(jd_text, dict) else "")
    edu_terms = _split_keyword_str(jd_text.get("education_keywords") if isinstance(jd_text, dict) else "")

    results = []
    for c in candidates:
        text = c.get("text") or ""
        work_experience_score = _keyword_coverage_from_terms(text, work_terms)
        skills_score = _keyword_coverage_from_terms(text, skills_terms)
        education_score = _keyword_coverage_from_terms(text, edu_terms)
        # full_text_score 继续使用对完整 JD 的召回得分（tfidf + coverage）
        full_text_score = float(c.get("full_text_score", 0.0))
        # 如果没有可用关键词，退化为旧逻辑的 full_text_score 作为三个维度基准，避免全部归零
        if not work_terms:
            work_experience_score = full_text_score
        if not skills_terms:
            skills_score = full_text_score
        if not edu_terms:
            education_score = full_text_score

        final_score = (
            0.45 * work_experience_score
            + 0.35 * skills_score
            + 0.10 * education_score
            + 0.10 * full_text_score
        )
        c["work_experience_score"] = float(work_experience_score)
        c["skills_score"] = float(skills_score)
        c["education_score"] = float(education_score)
        c["full_text_score"] = float(full_text_score)
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
    jd_features = {
        "jd_text": req.jd_text,
        "work_experience_keywords": req.work_experience_keywords,
        "skills_keywords": req.skills_keywords,
        "education_keywords": req.education_keywords,
    }
    rerank = _rerank_stage(recall_candidates, jd_features, req.top_k)
    return {
        "summary": {
            "total_resumes": len(req.resumes),
            "recall_count": len(recall_candidates),
            "top_k": req.top_k,
            "elapsed_ms": int((_now() - start) * 1000),
        },
        "results": rerank,
    }
