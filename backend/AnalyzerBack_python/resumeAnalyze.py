import re
from typing import Dict, Any, List, Tuple, Optional
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

# 可选：中文分词
try:
    import jieba
    HAS_JIEBA = True
except ImportError:
    HAS_JIEBA = False

HAN_RE = re.compile(r"[\u4e00-\u9fff]")  # 是否包含汉字


def contains_chinese(text: str) -> bool:
    return bool(HAN_RE.search(text or ""))


def tokenize_mixed(text: str) -> str:
    """
    返回给 TfidfVectorizer 的“空格分隔 token 字符串”
    - 中文：jieba 分词（如果安装了）
    - 英文：保留给 vectorizer 默认 token_pattern 也行，但这里统一做粗切分
    """
    text = (text or "").strip()
    if not text:
        return ""

    if contains_chinese(text):
        if not HAS_JIEBA:
            # 没装jieba时：退化为“按字符/标点切分”，效果一般，但至少不报错
            text = re.sub(r"[^\w\u4e00-\u9fff]+", " ", text)
            return " ".join([t for t in text.split() if len(t) > 1])

        # jieba 分词：再拼成空格分隔
        tokens = [t.strip() for t in jieba.cut(text) if t.strip()]
        # 过滤太短的 token
        tokens = [t for t in tokens if len(t) > 1]
        return " ".join(tokens)

    # 英文/其他语言：简单清洗
    text = re.sub(r"[^A-Za-z0-9]+", " ", text).lower()
    tokens = [t for t in text.split() if len(t) > 1]
    return " ".join(tokens)


def build_resume_text(resume_data: Dict[str, Any]) -> Tuple[str, str, str, str]:
    """
    返回：raw_text, skills_text, work_text, project_text
    便于字段加权
    """
    raw_text = resume_data.get("raw_text", "") or ""
    skills = resume_data.get("skills", []) or []
    if isinstance(skills, str):
        skills = [skills]
    keywords = resume_data.get("keywords", []) or []
    if isinstance(keywords, str):
        keywords = [keywords]
    skills_all = skills + keywords
    skills_text = " ".join(map(str, skills_all))

    work_parts = []
    for exp in resume_data.get("work_experience", []) or []:
        if isinstance(exp, dict):
            work_parts.append(f"{exp.get('position','')} {exp.get('description','')}")
        else:
            work_parts.append(str(exp))
    work_text = " ".join(work_parts)

    proj_parts = []
    for p in resume_data.get("projects", []) or []:
        if isinstance(p, dict):
            proj_parts.append(f"{p.get('name','')} {p.get('description','')}")
        else:
            proj_parts.append(str(p))
    project_text = " ".join(proj_parts)

    return raw_text, skills_text, work_text, project_text


def tfidf_cosine(text_a: str, text_b: str, max_features: int = 2000) -> Tuple[float, List[str]]:
    """
    返回：(similarity, top_terms)
    top_terms 是共同出现且权重高的 n-gram，用于解释
    """
    a = tokenize_mixed(text_a)
    b = tokenize_mixed(text_b)
    if not a or not b:
        return 0.0, []

    vectorizer = TfidfVectorizer(
        stop_words="english",      # 对英文有效；中文分词后不影响
        ngram_range=(1, 2),
        max_features=max_features,
        min_df=1
    )

    X = vectorizer.fit_transform([a, b])
    sim = float(cosine_similarity(X[0:1], X[1:2])[0][0])

    # 解释：找共同非零特征里，简历侧权重高的 top terms
    feature_names = vectorizer.get_feature_names_out()
    v0 = X[0].toarray().ravel()
    v1 = X[1].toarray().ravel()

    common_idx = (v0 > 0) & (v1 > 0)
    idxs = common_idx.nonzero()[0]
    # 按简历权重排序（也可以按 v0+v1）
    idxs_sorted = sorted(idxs, key=lambda i: v0[i] + v1[i], reverse=True)[:10]
    top_terms = [feature_names[i] for i in idxs_sorted]

    return sim, top_terms


def calculate_tfidf_similarity(resume_data: Dict[str, Any], job_description: str) -> Dict[str, Any]:
    """
    字段加权：skills/work/projects/raw
    """
    raw_text, skills_text, work_text, project_text = build_resume_text(resume_data)

    # 各字段分别算
    s_skills, terms_skills = tfidf_cosine(skills_text, job_description)
    s_work, terms_work = tfidf_cosine(work_text, job_description)
    s_proj, terms_proj = tfidf_cosine(project_text, job_description)
    s_raw, terms_raw = tfidf_cosine(raw_text, job_description)

    # 权重（可调）
    score = (
        0.40 * s_skills +
        0.35 * s_work +
        0.20 * s_proj +
        0.05 * s_raw
    )

    # 合并解释词（去重）
    top_terms = []
    for terms in [terms_skills, terms_work, terms_proj, terms_raw]:
        for t in terms:
            if t not in top_terms:
                top_terms.append(t)
    top_terms = top_terms[:15]

    return {
        "tfidf_score": float(max(0.0, min(score, 1.0))),
        "detail": {
            "skills_score": s_skills,
            "work_score": s_work,
            "projects_score": s_proj,
            "raw_score": s_raw
        },
        "top_terms": top_terms,
        "notes": "中文内容已进行分词（若安装jieba）后再进行TF-IDF。"
    }