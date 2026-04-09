package com.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 客户端配置（API URL、超时、模型名）
 * 绑定 application-local.yml 中 deepseek.client
 */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek.client")
public class DeepseekClientProperties {

    /**
     * 对话接口 URL
     */
    private String apiUrl;

    /**
     * 连接超时（秒）
     */
    private int connectTimeout;

    /**
     * 读取超时（秒）
     */
    private int readTimeout;

    /**
     * 写入超时（秒）
     */
    private int writeTimeout;

    /**
     * 单次请求总超时（秒，0 表示不限制）
     */
    private int callTimeout;

    /**
     * 最大重试次数（不含首次请求）
     */
    private int retry;

    /**
     * 重试初始退避时长（毫秒）
     */
    private long retryBackoffMs;

    /**
     * 重试最大退避时长（毫秒）
     */
    private long retryMaxBackoffMs;

    private Model model = new Model();

    @Data
    public static class Model {
        /**
         * 普通对话模型
         */
        private String chat;
        /**
         * 推理模型
         */
        private String reasoner;
    }
}
