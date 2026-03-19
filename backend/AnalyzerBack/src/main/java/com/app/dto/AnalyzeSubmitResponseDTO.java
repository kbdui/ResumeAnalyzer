package com.app.dto;

import lombok.Data;

/**
 * 提交异步大模型分析任务响应
 */
@Data
public class AnalyzeSubmitResponseDTO {
    private String analyzeTaskId;
    private String taskId;
    private String message;
}
