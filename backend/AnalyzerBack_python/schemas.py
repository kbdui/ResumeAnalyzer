"""
接口请求/响应类
"""
from typing import List

from pydantic import BaseModel

from models import ResumeDocument


class MatchPipelineRequest(BaseModel):
    """匹配 pipeline 的请求体：JD 文本 + 多份简历 + TopK/RecallK 控制参数。"""

    jd_text: str
    work_experience_keywords: str = ""
    skills_keywords: str = ""
    education_keywords: str = ""
    resumes: List[ResumeDocument]
    top_k: int = 20
    recall_k: int = 200
