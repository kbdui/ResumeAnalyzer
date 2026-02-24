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
