package com.app.request;

import com.app.config.PythonTaskSetProperties;
import lombok.Data;

/**
 * 按业务 taskId 提交 Python 匹配任务
 */
@Data
public class TaskSubmitMatchRequest {
    private String taskId;
    private String jdText;
    private Integer topK;
    private Integer recallK;
}
