package com.app.request;

import com.app.dto.ResumeTextDTO;
import lombok.Data;

import java.util.List;

/**
 * 提交给 Python FastAPI 的匹配任务请求
 */
@Data
public class PythonMatchTaskRequest {
    private String jdText;
    private List<ResumeTextDTO> resumes;
    private Integer topK = 20;
    private Integer recallK = 200;
}

