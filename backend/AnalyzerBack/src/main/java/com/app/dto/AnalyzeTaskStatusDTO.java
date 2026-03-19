package com.app.dto;

import lombok.Data;

/**
 * 异步大模型分析任务状态
 */
@Data
public class AnalyzeTaskStatusDTO {
    private String analyzeTaskId;
    private String taskId;
    private String status;
    private Integer total;
    private Integer successCount;
    private Integer failedCount;
    private String error;
    private Long startedAtMs;
    private Long endedAtMs;
}
