from __future__ import annotations

import re
import time
from typing import Any, Dict, List, Tuple

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

_SECTION_HEADING_MAP = {
    "education": [
        "教育背景",
        "教育经历",
        "教育信息",
    ],
    "skills": [
        "专业技能",
        "技能特长",
        "技能清单",
        "技术栈",
        "专业能力",
        "掌握技能",
        "核心技能",
    ],
    "work": [
        "工作经历",
        "实习经历",
        "实践经历",
        "项目经历",
        "科研经历",
        "实践项目",
    ],
}

_KEYWORD_STOPWORDS = {
    "work": {
        "工作",
        "经验",
        "负责",
        "参与",
        "相关",
        "经历",
        "岗位",
        "开发",
        "工程",
        "能力",
    },
    "skills": {
        "熟悉",
        "了解",
        "掌握",
        "具备",
        "相关",
        "技能",
        "技术",
        "开发",
        "工程",
    },
    "education": {
        "教育",
        "学历",
        "本科",
        "专科",
        "硕士",
        "博士",
        "专业",
        "课程",
        "背景",
        "相关",
    },
}


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


def _clean_text(text: str) -> str:
    return re.sub(r"\s+", " ", (text or "").replace("\ufeff", " ")).strip()


def _match_section_heading(line: str) -> str | None:
    stripped = _clean_text(line).rstrip("：:;；")
    if not stripped:
        return None
    for section, headings in _SECTION_HEADING_MAP.items():
        for heading in headings:
            if stripped == heading or stripped.startswith(heading + "：") or stripped.startswith(heading + ":"):
                return section
    return None


def _split_resume_sections(text: str) -> Dict[str, str]:
    buckets: Dict[str, List[str]] = {"full": []}
    current = "full"
    for raw_line in (text or "").splitlines():
        line = raw_line.strip()
        if not line:
            continue
        heading = _match_section_heading(line)
        if heading:
            current = heading
            buckets.setdefault(current, [])
            continue
        buckets.setdefault(current, []).append(line)
        buckets["full"].append(line)
    return {key: _clean_text(" ".join(value)) for key, value in buckets.items()}


def _normalize_keyword_terms(keyword_str: str, dimension: str) -> List[str]:
    stopwords = _KEYWORD_STOPWORDS.get(dimension, set())
    normalized: List[str] = []
    seen = set()
    for raw_term in (keyword_str or "").split(" "):
        term = _clean_text(raw_term)
        if not term or len(term) <= 1 or term in stopwords:
            continue
        if term not in seen:
            seen.add(term)
            normalized.append(term)
    return normalized


def _safe_tfidf_score(text_a: str, text_b: str) -> float:
    score, _ = tfidf_cosine(text_a, text_b)
    return float(score)


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


def _keyword_coverage_from_terms(resume_text: str, jd_terms: List[str]) -> float:
    if not jd_terms:
        return 0.0
    pool_text = resume_text or ""
    matched = sum(1 for term in jd_terms if term in pool_text)
    return matched / len(jd_terms)


def _keyword_coverage(resume: ResumeDocument, jd_text: str) -> float:
    jd_terms = _extract_keywords(jd_text)
    return _keyword_coverage_from_terms(resume.text or "", jd_terms)


def _build_dimension_texts(raw_text: str) -> Tuple[str, str, str, str]:
    sections = _split_resume_sections(raw_text)
    full_text = sections.get("full", "")
    work_text = _clean_text(" ".join(filter(None, [sections.get("work", ""), sections.get("skills", "")])))
    skills_text = _clean_text(" ".join(filter(None, [sections.get("skills", ""), sections.get("work", "")])))
    education_text = sections.get("education", "")
    return (
        work_text or full_text,
        skills_text or full_text,
        education_text or full_text,
        full_text,
    )


def _dimension_score(section_text: str, keyword_terms: List[str], fallback_text: str, jd_text: str) -> float:
    base_text = _clean_text(section_text) or _clean_text(fallback_text)
    if not base_text:
        return 0.0

    keyword_text = " ".join(keyword_terms)
    coverage = _keyword_coverage_from_terms(base_text, keyword_terms) if keyword_terms else 0.0
    keyword_similarity = _safe_tfidf_score(base_text, keyword_text) if keyword_text else 0.0
    jd_similarity = _safe_tfidf_score(base_text, jd_text) if jd_text else 0.0

    if keyword_terms:
        score = 0.50 * coverage + 0.25 * keyword_similarity + 0.25 * jd_similarity
    else:
        score = jd_similarity
    return float(max(0.0, min(score, 1.0)))


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


def _attach_presentation_scores(results: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    if not results:
        return results

    raw_scores = [float(item.get("final_score", 0.0)) for item in results]
    min_score = min(raw_scores)
    max_score = max(float(item.get("final_score", 0.0)) for item in results)

    if max_score <= min_score:
        for item in results:
            item["raw_final_score"] = float(item.get("final_score", 0.0))
            item["display_score"] = 100.0
        return results

    for item in results:
        raw_score = float(item.get("final_score", 0.0))
        item["raw_final_score"] = raw_score
        item["display_score"] = 70.0 + 30.0 * (raw_score - min_score) / (max_score - min_score)
    return results


def _rerank_stage(candidates: List[Dict[str, Any]], jd_text: Dict[str, Any], top_k: int) -> Dict[str, Any]:
    jd_full_text = jd_text.get("jd_text") if isinstance(jd_text, dict) else ""
    embedder = _get_embedder()
    embedding_fallback = embedder is None

    work_terms = _normalize_keyword_terms(
        jd_text.get("work_experience_keywords") if isinstance(jd_text, dict) else "",
        "work",
    )
    skills_terms = _normalize_keyword_terms(
        jd_text.get("skills_keywords") if isinstance(jd_text, dict) else "",
        "skills",
    )
    edu_terms = _normalize_keyword_terms(
        jd_text.get("education_keywords") if isinstance(jd_text, dict) else "",
        "education",
    )

    results = []
    for c in candidates:
        text = c.get("text") or ""
        work_text, skills_text, education_text, full_resume_text = _build_dimension_texts(text)

        work_experience_score = _dimension_score(work_text, work_terms, full_resume_text, jd_full_text)
        skills_score = _dimension_score(skills_text, skills_terms, full_resume_text, jd_full_text)
        education_score = _dimension_score(education_text, edu_terms, full_resume_text, jd_full_text)
        full_text_score = float(c.get("full_text_score", 0.0))
        embedding_score = _embed_score(c, jd_full_text) if jd_full_text else 0.0

        if embedding_fallback:
            final_score = (
                0.40 * work_experience_score
                + 0.30 * skills_score
                + 0.15 * education_score
                + 0.15 * full_text_score
            )
        else:
            final_score = (
                0.10 * work_experience_score
                + 0.10 * skills_score
                + 0.10 * education_score
                + 0.20 * full_text_score
                + 0.50 * embedding_score
            )

        c["work_experience_score"] = float(work_experience_score)
        c["skills_score"] = float(skills_score)
        c["education_score"] = float(education_score)
        c["full_text_score"] = float(full_text_score)
        c["embedding_score"] = float(embedding_score)
        c["final_score"] = float(final_score)
        results.append(c)
    results.sort(key=lambda x: x["final_score"], reverse=True)
    results = _attach_presentation_scores(results)
    return {
        "model": EMBEDDING_MODEL_NAME,
        "embedding_fallback": embedding_fallback,
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
