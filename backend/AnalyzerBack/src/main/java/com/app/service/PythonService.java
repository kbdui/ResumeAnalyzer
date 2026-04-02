package com.app.service;


/**
 * 调用python的fastAPI相关服务
 */
import com.app.request.ResumeHybridRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class PythonService {

    @Value("${python.fastapi.base-url:http://127.0.0.1:8000}")
    private String baseUrl;

    private final ObjectMapper objectMapper;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    public PythonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 提交异步匹配任务，返回 taskId
     */
    public String submitHybridTask(ResumeHybridRequest payload) throws IOException {
        String body = objectMapper.writeValueAsString(convertPayload(payload));
        Request request = new Request.Builder()
                .url(baseUrl + "/tasks/match-pipeline")
                .post(RequestBody.create(body, MediaType.get("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Python任务提交失败: " + response.code() + " - " + response.message());
            }
            JsonNode root = objectMapper.readTree(response.body().string());
            String taskId = root.path("task_id").asText();
            if (taskId == null || taskId.isBlank()) {
                throw new IOException("Python返回 task_id 为空");
            }
            return taskId;
        }
    }

    /**
     * 按字段传参，调用上方重构函数
     */
    public String submitHybridTask(String jdText,
                                   List<com.app.dto.TextDTO> resumes,
                                   Integer topK,
                                   Integer recallK) throws IOException {
        ResumeHybridRequest request = new ResumeHybridRequest();
        request.setJdText(jdText);
        request.setResumes(resumes);
        request.setTopK(topK == null ? 20 : topK);
        request.setRecallK(recallK == null ? 200 : recallK);
        return submitHybridTask(request);
    }

    /**
     * 查询异步任务状态与结果
     */
    public JsonNode getTask(String taskId) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/tasks/" + taskId)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("查询Python任务失败: " + response.code() + " - " + response.message());
            }
            return objectMapper.readTree(response.body().string());
        }
    }

    private JsonNode convertPayload(ResumeHybridRequest payload) {
        JsonNode root = objectMapper.valueToTree(payload);
        // Java 使用驼峰，Python API 使用下划线字段名
        if (root.isObject()) {
            ((tools.jackson.databind.node.ObjectNode) root).set("jd_text", root.get("jdText"));
            ((tools.jackson.databind.node.ObjectNode) root).set("top_k", root.get("topK"));
            ((tools.jackson.databind.node.ObjectNode) root).set("recall_k", root.get("recallK"));
            // taskId 仅用于 controller 层，不属于 Python payload
            ((tools.jackson.databind.node.ObjectNode) root).remove("taskId");
            ((tools.jackson.databind.node.ObjectNode) root).remove("jdText");
            ((tools.jackson.databind.node.ObjectNode) root).remove("topK");
            ((tools.jackson.databind.node.ObjectNode) root).remove("recallK");
        }
        return root;
    }
}
