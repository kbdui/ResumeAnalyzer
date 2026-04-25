package com.app.request;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class DeepseekRequest {
    private String model;
    private List<Message> messages;

    @SerializedName("reasoning_effort")
    private String reasoningEffort;

    private Thinking thinking;

    private Map<String, Object> response_format;

    @Data
    @Builder
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    @Builder
    public static class Thinking {
        private String type;
    }
}
