package com.app.request;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class DeepseekRequest {
    private String model;
    private List<Message> messages;
    private Double temperature; // 新增属性
    private Map<String, Object> response_format; // 新增属性

    @Data
    @Builder
    public static class Message {
        private String role;
        private String content;
    }
}
