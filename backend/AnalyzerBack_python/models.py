"""
数据模型类
"""
from typing import Any, List, Optional

from pydantic import BaseModel, ConfigDict, Field


class ResumeDocument(BaseModel):
    """单份简历的标准化输入结构（pipeline 输入的一部分）。"""

    model_config = ConfigDict(populate_by_name=True)

    resume_id: Optional[str] = Field(default=None, alias="resumeId")
    file_name: Optional[str] = Field(default=None, alias="fileName")
    text: str = ""
    raw_text: str = Field(default="", alias="rawText")
    summary: str = ""
    skills: List[str] = Field(default_factory=list)
    keywords: List[str] = Field(default_factory=list)
    work_experience: List[Any] = Field(default_factory=list, alias="workExperience")
    projects: List[Any] = Field(default_factory=list)
    education: List[Any] = Field(default_factory=list)
    industry_tags: List[str] = Field(default_factory=list, alias="industryTags")
    role_tags: List[str] = Field(default_factory=list, alias="roleTags")
    years_of_experience: Optional[float] = Field(default=None, alias="yearsOfExperience")
    management_level: Optional[str] = Field(default=None, alias="managementLevel")
    hard_filter_result: Optional[str] = Field(default=None, alias="hardFilterResult")
