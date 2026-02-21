package com.app.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import okhttp3.*;
import com.app.request.DeepseekRequest;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class DeepseekClient {
    //如果使用硅基平台，API_URL修改为 "https://api.siliconflow.cn/v1/chat/completions"
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    // private static final String TENCENT_API_URL = "https://api.lkeap.cloud.tencent.com/v1/chat/completions";

    // 加上超时配置
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private final Gson gson = new Gson();

    public String getResponse(String apiKey, String prompt) throws IOException {
        // 构建请求体
        DeepseekRequest.Message message = DeepseekRequest.Message.builder()
                .role("user")
                .content(prompt).build();
        //如果使用硅基平台，model修改为 "deepseek-ai/DeepSeek-V3"
        DeepseekRequest requestBody = DeepseekRequest.builder()
                .model("deepseek-chat")
                .messages(Collections.singletonList(message))
                .build();

        // 创建HTTP请求
        Request request = new Request.Builder()
                .url(API_URL)
                .post(RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json")))
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        // 发送请求并处理响应
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return response.body().string();
        }
    }

    public String getResponse(String apiKey, String prompt, boolean useSiliconFlow) throws IOException {
//        String apiUrl = useSiliconFlow ? SILICON_API_URL : TENCENT_API_URL;
//        String model = useSiliconFlow ? "deepseek-ai/DeepSeek-V3" : "deepseek-chat";
        String model = useSiliconFlow ? "deepseek-reasoner" : "deepseek-chat";

        // 构建请求体
        DeepseekRequest.Message message = DeepseekRequest.Message.builder()
                .role("user")
                .content(prompt).build();

        DeepseekRequest requestBody = DeepseekRequest.builder()
                .model(model)
                .messages(Collections.singletonList(message))
                .temperature(0.7)
                .response_format(Collections.singletonMap("type", "json_object")) // 强制JSON输出
                .build();

        // 创建HTTP请求
//        Request request = new Request.Builder()
//                .url(apiUrl)
//                .post(RequestBody.create(objectMapper.writeValueAsString(requestBody),
//                        MediaType.get("application/json")))
//                .addHeader("Authorization", "Bearer " + apiKey)
//                .build();
        Request request = new Request.Builder()
                .url(API_URL)
                .post(RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json")))
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        // 发送请求并处理响应
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API请求失败: " + response.code() + " - " + response.message());
            }
            return response.body().string();
        }
    }
}
