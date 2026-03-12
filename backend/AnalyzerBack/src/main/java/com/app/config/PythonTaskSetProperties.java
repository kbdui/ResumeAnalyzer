package com.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "python-task")
public class PythonTaskSetProperties {
    /**
     * 一阶段筛选数量
     */
    private int recallK;

    /**
     * 二阶段筛选数量
     */
    private int topK;
}
