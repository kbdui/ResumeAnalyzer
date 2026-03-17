"""
数据模型类
"""
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class ResumeDocument(BaseModel):
    """单份简历的标准化输入结构（pipeline 输入的一部分）。"""

    model_config = ConfigDict(populate_by_name=True)

    resume_id: Optional[str] = Field(default=None, alias="resumeId")
    file_name: Optional[str] = Field(default=None, alias="fileName")
    text: str = ""
