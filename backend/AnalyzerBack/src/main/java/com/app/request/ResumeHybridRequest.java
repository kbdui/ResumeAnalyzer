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
    private List<TextDTO> resumes;
    private Integer topK = 20;
    private Integer recallK = 200;
}

