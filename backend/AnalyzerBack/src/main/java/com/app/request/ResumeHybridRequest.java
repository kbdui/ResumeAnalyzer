package com.app.request;

import com.app.dto.TextDTO;
import lombok.Data;

import java.util.List;

/**
 * 提交给 Python FastAPI 的匹配任务请求
 */
@Data
public class ResumeHybridRequest {
    /**
     * taskId和resumes二选一
     */
    private String taskId;
    private String jdText;
    /**
     * 由 LLM 从 JD 中抽取的关键词（空格分隔）
     */
    private String workExperienceKeywords;
    private String skillsKeywords;
    private String educationKeywords;
    private List<TextDTO> resumes;
    private Integer topK = 20;
    private Integer recallK = 200;
}

