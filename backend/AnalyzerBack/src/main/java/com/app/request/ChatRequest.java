package com.app.request;

import lombok.Data;

@Data
public class ChatRequest {
    /**
     * 用户发送的问题/消息
     */
    private String message;
}
