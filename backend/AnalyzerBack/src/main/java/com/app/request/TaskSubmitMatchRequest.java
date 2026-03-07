package com.app.request;

import lombok.Data;

/**
 * 按业务 taskId 提交 Python 匹配任务
 */
@Data
public class TaskSubmitMatchRequest {
    private String taskId;
    private String jdText;
    private Integer topK = 20;
    private Integer recallK = 200;
}
