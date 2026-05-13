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
    "summary": [
        "个人简介",
        "自我评价",
        "职业概述",
        "求职意向",
        "个人总结",
    ],
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
    "project": [
        "项目经历",
        "项目经验",
        "活动经历",
        "科研项目",
        "实践项目",
        "品牌项目",
    ],
    "work": [
        "工作经历",
        "实习经历",
        "实践经历",
        "岗位经历",
        "职业经历",
        "工作经验",
    ],
}

_KEYWORD_STOPWORDS = {
    "work": {"工作", "经验", "负责", "参与", "相关", "经历", "岗位", "能力", "推进", "支持"},
    "project": {"项目", "活动", "负责", "参与", "相关", "经验", "能力", "推进", "支持"},
    "skills": {"熟悉", "了解", "掌握", "具备", "相关", "技能", "技术", "能力", "使用"},
    "education": {"教育", "学历", "本科", "专科", "硕士", "博士", "专业", "课程", "背景", "相关"},
}

_JOB_FAMILY_RULES: Dict[str, Dict[str, List[str]]] = {
    "market_sales": {
        "positive": [
            "市场", "营销", "品牌", "传播", "公关", "活动", "策划", "推广", "增长", "用户增长",
            "内容", "新媒体", "社交媒体", "渠道", "campaign", "广告", "合作伙伴", "市场调研",
            "竞品", "复盘", "商家拓展", "业务拓展", "客户增长",
        ],
        "supportive": [
            "销售", "客户关系", "商务拓展", "运营", "项目管理", "数据分析", "excel", "powerpoint",
            "crm", "salesforce", "google analytics", "seo",
        ],
        "negative": [
            "会计", "财务", "银行柜员", "教师", "讲师", "软件工程师", "后端开发", "算法工程师",
            "纯审计", "出纳", "护士", "临床",
        ],
    },
    "software": {
        "positive": ["开发", "后端", "前端", "java", "python", "spring", "react", "vue", "算法", "测试"],
        "supportive": ["项目", "系统", "接口", "部署", "数据库", "linux", "docker"],
        "negative": ["市场", "品牌", "公关", "销售主管", "导购", "会计", "教师"],
    },
    "finance": {
        "positive": ["金融", "银行", "财务", "会计", "预算", "审计", "风控", "投融资", "报表"],
        "supportive": ["excel", "oracle", "sap", "预算编制", "差异分析"],
        "negative": ["软件工程师", "教师", "公关", "设计师"],
    },
    "education": {
        "positive": ["教师", "教学", "课程", "学校", "教研", "讲师", "班级管理"],
        "supportive": ["沟通", "培训", "家校", "课堂", "评估"],
        "negative": ["软件工程师", "银行业", "会计总监", "品牌经理"],
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


def _clean_text(text: Any) -> str:
    return re.sub(r"\s+", " ", str(text or "").replace("\ufeff", " ")).strip()


def _textify_item(item: Any) -> str:
    if item is None:
        return ""
    if isinstance(item, str):
        return _clean_text(item)
    if isinstance(item, dict):
        pieces: List[str] = []
        for value in item.values():
            text = _clean_text(value)
            if text:
                pieces.append(text)
        return _clean_text(" ".join(pieces))
    if isinstance(item, (list, tuple, set)):
        return _clean_text(" ".join(_textify_item(x) for x in item))
    return _clean_text(item)


def _join_unique_texts(*texts: str) -> str:
    seen = set()
    merged: List[str] = []
    for text in texts:
        normalized = _clean_text(text)
        if normalized and normalized not in seen:
            seen.add(normalized)
            merged.append(normalized)
    return _clean_text(" ".join(merged))


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
    current = "summary"
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
    raw_items = re.split(r"[\s,，;；/|]+", keyword_str or "")
    normalized: List[str] = []
    seen = set()
    for raw_term in raw_items:
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
    token_space = set(tokenize_mixed(resume_text).split())
    if not token_space:
        return 0.0
    matched = 0
    for term in jd_terms:
        term_tokens = [t for t in tokenize_mixed(term).split() if t]
        if not term_tokens:
            continue
        if all(token in token_space for token in term_tokens):
            matched += 1
    return matched / len(jd_terms)


def _infer_job_family(jd_text: str, req: MatchPipelineRequest) -> str:
    source = _join_unique_texts(
        jd_text,
        req.work_experience_keywords,
        req.skills_keywords,
        req.education_keywords,
        req.project_keywords,
        req.preferred_roles,
        req.preferred_industries,
    ).lower()
    best_family = "general"
    best_score = 0
    for family, rule in _JOB_FAMILY_RULES.items():
        score = 0
        for token in rule["positive"]:
            if token.lower() in source:
                score += 2
        for token in rule["supportive"]:
            if token.lower() in source:
                score += 1
        if score > best_score:
            best_score = score
            best_family = family
    return best_family


def _build_jd_profile(req: MatchPipelineRequest) -> Dict[str, Any]:
    family = _infer_job_family(req.jd_text, req)
    family_rule = _JOB_FAMILY_RULES.get(family, {"positive": [], "supportive": [], "negative": []})
    work_terms = _normalize_keyword_terms(req.work_experience_keywords, "work")
    project_terms = _normalize_keyword_terms(req.project_keywords, "project")
    skills_terms = _normalize_keyword_terms(req.skills_keywords, "skills")
    education_terms = _normalize_keyword_terms(req.education_keywords, "education")
    role_terms = _normalize_keyword_terms(req.preferred_roles, "work")
    industry_terms = _normalize_keyword_terms(req.preferred_industries, "work")
    fallback_terms = _extract_keywords(req.jd_text, max_terms=60)
    if not work_terms:
        work_terms = fallback_terms[:20]
    if not project_terms:
        project_terms = fallback_terms[:20]
    if not skills_terms:
        skills_terms = fallback_terms[:20]
    return {
        "jd_text": _clean_text(req.jd_text),
        "job_family": family,
        "family_positive_terms": [x.lower() for x in family_rule.get("positive", [])],
        "family_supportive_terms": [x.lower() for x in family_rule.get("supportive", [])],
        "family_negative_terms": [x.lower() for x in family_rule.get("negative", [])],
        "work_terms": work_terms,
        "project_terms": project_terms,
        "skills_terms": skills_terms,
        "education_terms": education_terms,
        "role_terms": role_terms,
        "industry_terms": industry_terms,
        "fallback_terms": fallback_terms,
    }


def _build_resume_segments(resume: ResumeDocument) -> Dict[str, Any]:
    raw_text = _join_unique_texts(resume.text, resume.raw_text)
    sections = _split_resume_sections(raw_text)

    summary_text = _join_unique_texts(resume.summary, sections.get("summary", ""))
    work_text = _join_unique_texts(sections.get("work", ""), _textify_item(resume.work_experience))
    project_text = _join_unique_texts(sections.get("project", ""), _textify_item(resume.projects))
    skills_text = _join_unique_texts(
        sections.get("skills", ""),
        _textify_item(resume.skills),
        _textify_item(resume.keywords),
    )
    education_text = _join_unique_texts(sections.get("education", ""), _textify_item(resume.education))

    full_text = _join_unique_texts(summary_text, work_text, project_text, skills_text, education_text, sections.get("full", ""))
    work_project_text = _join_unique_texts(work_text, project_text)

    tags_text = _join_unique_texts(
        _textify_item(resume.industry_tags),
        _textify_item(resume.role_tags),
        resume.management_level,
    )

    return {
        "summary": summary_text or full_text,
        "work": work_text or full_text,
        "project": project_text or work_text or full_text,
        "skills": skills_text or full_text,
        "education": education_text or full_text,
        "work_project": work_project_text or full_text,
        "full": full_text,
        "tags": tags_text,
        "industry_tags": [_clean_text(x).lower() for x in (resume.industry_tags or []) if _clean_text(x)],
        "role_tags": [_clean_text(x).lower() for x in (resume.role_tags or []) if _clean_text(x)],
        "years_of_experience": resume.years_of_experience,
        "management_level": _clean_text(resume.management_level).lower(),
        "hard_filter_result": _clean_text(resume.hard_filter_result).lower(),
    }


def _dimension_score(section_text: str, keyword_terms: List[str], fallback_text: str, jd_text: str) -> float:
    base_text = _clean_text(section_text) or _clean_text(fallback_text)
    if not base_text:
        return 0.0

    keyword_text = " ".join(keyword_terms)
    coverage = _keyword_coverage_from_terms(base_text, keyword_terms) if keyword_terms else 0.0
    keyword_similarity = _safe_tfidf_score(base_text, keyword_text) if keyword_text else 0.0
    jd_similarity = _safe_tfidf_score(base_text, jd_text) if jd_text else 0.0

    if keyword_terms:
        score = 0.45 * coverage + 0.20 * keyword_similarity + 0.35 * jd_similarity
    else:
        score = jd_similarity
    return float(max(0.0, min(score, 1.0)))


def _role_alignment_score(segments: Dict[str, Any], jd_profile: Dict[str, Any]) -> Tuple[float, List[str]]:
    positive_terms = jd_profile.get("family_positive_terms", [])
    supportive_terms = jd_profile.get("family_supportive_terms", [])
    negative_terms = jd_profile.get("family_negative_terms", [])
    pool = _join_unique_texts(
        segments.get("work", ""),
        segments.get("project", ""),
        segments.get("summary", ""),
        segments.get("tags", ""),
    ).lower()
    reasons: List[str] = []
    score = 0.0

    positive_hits = sum(1 for term in positive_terms if term and term in pool)
    supportive_hits = sum(1 for term in supportive_terms if term and term in pool)
    negative_hits = sum(1 for term in negative_terms if term and term in pool)

    if positive_hits:
        score += min(0.65, 0.18 * positive_hits)
        reasons.append(f"命中核心岗位信号{positive_hits}项")
    if supportive_hits:
        score += min(0.25, 0.06 * supportive_hits)
        reasons.append(f"命中辅助岗位信号{supportive_hits}项")
    if segments.get("role_tags"):
        role_hits = sum(1 for term in jd_profile.get("role_terms", []) if term.lower() in segments.get("role_tags", []))
        if role_hits:
            score += 0.10
            reasons.append("角色标签与JD偏好一致")
    if segments.get("industry_tags"):
        industry_hits = sum(1 for term in jd_profile.get("industry_terms", []) if term.lower() in segments.get("industry_tags", []))
        if industry_hits:
            score += 0.08
            reasons.append("行业标签与JD偏好一致")

    if negative_hits and positive_hits == 0:
        score -= min(0.35, 0.12 * negative_hits)
        reasons.append(f"出现非目标岗位信号{negative_hits}项")

    return float(max(0.0, min(score, 1.0))), reasons


def _experience_score(segments: Dict[str, Any], jd_profile: Dict[str, Any]) -> Tuple[float, List[str]]:
    years = segments.get("years_of_experience")
    level = segments.get("management_level", "")
    hard_filter_result = segments.get("hard_filter_result", "")
    score = 0.0
    reasons: List[str] = []

    if isinstance(years, (int, float)):
        if years >= 5:
            score += 0.70
            reasons.append("工作年限较充足")
        elif years >= 2:
            score += 0.50
            reasons.append("具备一定工作年限")
        elif years > 0:
            score += 0.30
            reasons.append("工作年限有限")

    if level in {"senior", "lead", "manager", "director", "高级", "主管", "经理", "总监"}:
        score += 0.20
        reasons.append("具备较高层级经历")

    if hard_filter_result and "pass" in hard_filter_result:
        score += 0.10
        reasons.append("已通过上游硬过滤")

    return float(max(0.0, min(score, 1.0))), reasons


def _negative_penalty(segments: Dict[str, Any], jd_profile: Dict[str, Any]) -> Tuple[float, List[str]]:
    family = jd_profile.get("job_family", "general")
    work_text = _join_unique_texts(segments.get("work", ""), segments.get("project", "")).lower()
    role_score, _ = _role_alignment_score(segments, jd_profile)
    penalty = 0.0
    reasons: List[str] = []

    if family == "market_sales":
        if role_score < 0.20 and any(term in work_text for term in ["会计", "财务", "银行", "教师", "软件", "开发"]):
            penalty += 0.10
            reasons.append("主经历偏离市场销售岗位")
        if not any(term in work_text for term in ["市场", "营销", "品牌", "活动", "推广", "增长", "传播"]):
            penalty += 0.08
            reasons.append("缺少直接市场活动或品牌经历")
        if any(term in work_text for term in ["销售"]):
            if not any(term in work_text for term in ["活动", "品牌", "策划", "传播", "推广", "内容"]):
                penalty += 0.05
                reasons.append("销售经验较多但营销策划信号偏弱")

    return float(min(0.35, penalty)), reasons


def _recall_stage(req: MatchPipelineRequest, jd_profile: Dict[str, Any]) -> List[Dict[str, Any]]:
    candidates: List[Dict[str, Any]] = []
    jd_text = jd_profile["jd_text"]
    work_terms = jd_profile["work_terms"]
    project_terms = jd_profile["project_terms"]
    skills_terms = jd_profile["skills_terms"]

    for idx, resume in enumerate(req.resumes):
        segments = _build_resume_segments(resume)

        work_project_tfidf, top_terms = tfidf_cosine(segments["work_project"], jd_text)
        skills_tfidf, _ = tfidf_cosine(segments["skills"], jd_text)
        work_project_coverage = _keyword_coverage_from_terms(
            segments["work_project"],
            list(dict.fromkeys(work_terms + project_terms)),
        )
        skills_coverage = _keyword_coverage_from_terms(segments["skills"], skills_terms)
        role_alignment, role_reasons = _role_alignment_score(segments, jd_profile)

        recall_score = (
            0.45 * work_project_tfidf
            + 0.15 * skills_tfidf
            + 0.20 * work_project_coverage
            + 0.10 * skills_coverage
            + 0.10 * role_alignment
        )

        if work_project_tfidf < 0.02 and work_project_coverage < 0.04 and role_alignment < 0.05:
            continue

        candidates.append(
            {
                "resume_id": resume.resume_id or str(idx),
                "file_name": resume.file_name or "",
                "text": segments["full"],
                "segments": segments,
                "recall_score": float(recall_score),
                "tfidf_score": float(work_project_tfidf),
                "keyword_coverage": float(work_project_coverage),
                "skills_recall_score": float(skills_tfidf),
                "skills_coverage": float(skills_coverage),
                "role_alignment_score": float(role_alignment),
                "role_alignment_reasons": role_reasons,
                "top_terms": top_terms,
                "full_text_score": float(recall_score),
            }
        )

    candidates.sort(key=lambda x: x["recall_score"], reverse=True)
    return candidates[: max(1, req.recall_k)]


def _embed_score(candidate: Dict[str, Any], jd_text: str) -> float:
    embedder = _get_embedder()
    if embedder is None:
        return float(candidate.get("tfidf_score", 0.0))

    segments = candidate.get("segments") or {}
    work_project = segments.get("work_project") or candidate.get("text") or ""
    full_text = segments.get("full") or candidate.get("text") or ""
    if not work_project.strip() or not jd_text.strip():
        return 0.0
    vec = embedder.encode([work_project, full_text, jd_text], normalize_embeddings=True)
    work_project_score = float(np.dot(vec[0], vec[2]))
    full_score = float(np.dot(vec[1], vec[2]))
    return 0.7 * work_project_score + 0.3 * full_score


def _attach_presentation_scores(results: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    if not results:
        return results

    raw_scores = [float(item.get("final_score", 0.0)) for item in results]
    min_score = min(raw_scores)
    max_score = max(raw_scores)

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


def _rerank_stage(candidates: List[Dict[str, Any]], jd_profile: Dict[str, Any], top_k: int) -> Dict[str, Any]:
    jd_full_text = jd_profile.get("jd_text", "")
    embedder = _get_embedder()
    embedding_fallback = embedder is None

    results = []
    for c in candidates:
        segments = c.get("segments") or {}
        full_resume_text = segments.get("full", "")

        work_experience_score = _dimension_score(segments.get("work", ""), jd_profile["work_terms"], full_resume_text, jd_full_text)
        project_score = _dimension_score(segments.get("project", ""), jd_profile["project_terms"], full_resume_text, jd_full_text)
        skills_score = _dimension_score(segments.get("skills", ""), jd_profile["skills_terms"], full_resume_text, jd_full_text)
        education_score = _dimension_score(segments.get("education", ""), jd_profile["education_terms"], full_resume_text, jd_full_text)
        summary_score = _safe_tfidf_score(segments.get("summary", ""), jd_full_text) if jd_full_text else 0.0
        full_text_score = float(c.get("full_text_score", 0.0))
        role_alignment_score, role_alignment_reasons = _role_alignment_score(segments, jd_profile)
        experience_score, experience_reasons = _experience_score(segments, jd_profile)
        penalty, penalty_reasons = _negative_penalty(segments, jd_profile)
        embedding_score = _embed_score(c, jd_full_text) if jd_full_text else 0.0

        if embedding_fallback:
            final_score = (
                0.26 * work_experience_score
                + 0.20 * project_score
                + 0.16 * skills_score
                + 0.08 * education_score
                + 0.10 * summary_score
                + 0.10 * role_alignment_score
                + 0.10 * experience_score
            ) - penalty
        else:
            final_score = (
                0.20 * work_experience_score
                + 0.16 * project_score
                + 0.12 * skills_score
                + 0.06 * education_score
                + 0.08 * summary_score
                + 0.10 * role_alignment_score
                + 0.08 * experience_score
                + 0.08 * full_text_score
                + 0.12 * embedding_score
            ) - penalty

        c["work_experience_score"] = float(work_experience_score)
        c["project_score"] = float(project_score)
        c["skills_score"] = float(skills_score)
        c["education_score"] = float(education_score)
        c["summary_score"] = float(summary_score)
        c["full_text_score"] = float(full_text_score)
        c["role_alignment_score"] = float(role_alignment_score)
        c["experience_score"] = float(experience_score)
        c["embedding_score"] = float(embedding_score)
        c["negative_penalty"] = float(penalty)
        c["role_alignment_reasons"] = role_alignment_reasons
        c["experience_reasons"] = experience_reasons
        c["penalty_reasons"] = penalty_reasons
        c["final_score"] = float(max(0.0, final_score))
        results.append(c)

    results.sort(key=lambda x: x["final_score"], reverse=True)
    results = _attach_presentation_scores(results)
    return {
        "model": EMBEDDING_MODEL_NAME,
        "embedding_fallback": embedding_fallback,
        "embedding_error": _embedder_error,
        "job_family": jd_profile.get("job_family", "general"),
        "items": results[: max(1, top_k)],
    }


def run_pipeline(req: MatchPipelineRequest) -> Dict[str, Any]:
    start = _now()
    jd_profile = _build_jd_profile(req)
    recall_candidates = _recall_stage(req, jd_profile)
    rerank = _rerank_stage(recall_candidates, jd_profile, req.top_k)
    return {
        "summary": {
            "total_resumes": len(req.resumes),
            "recall_count": len(recall_candidates),
            "top_k": req.top_k,
            "elapsed_ms": int((_now() - start) * 1000),
            "job_family": jd_profile.get("job_family", "general"),
        },
        "results": rerank,
    }
