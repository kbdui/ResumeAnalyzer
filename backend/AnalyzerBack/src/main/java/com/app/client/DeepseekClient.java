package com.app.client;

import com.app.config.DeepseekClientProperties;
import com.app.request.DeepseekRequest;
import com.google.gson.Gson;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class DeepseekClient {

    private final OkHttpClient client;
    private final Gson gson = new Gson();
    private final DeepseekClientProperties properties;

    public DeepseekClient(DeepseekClientProperties properties) {
        this.properties = properties;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout(), TimeUnit.SECONDS)
                .readTimeout(properties.getReadTimeout(), TimeUnit.SECONDS)
                .build();
    }

    public String getResponse(String apiKey, String prompt) throws IOException {
        DeepseekRequest.Message message = DeepseekRequest.Message.builder()
                .role("user")
                .content(prompt).build();
        DeepseekRequest requestBody = DeepseekRequest.builder()
                .model(properties.getModel().getChat())
                .messages(Collections.singletonList(message))
                .build();

        Request request = new Request.Builder()
                .url(properties.getApiUrl())
                .post(RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json")))
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return response.body().string();
        }
    }

    public String getResponse(String apiKey, String prompt, boolean useSiliconFlow) throws IOException {
        String model = useSiliconFlow ? properties.getModel().getReasoner() : properties.getModel().getChat();

        DeepseekRequest.Message message = DeepseekRequest.Message.builder()
                .role("user")
                .content(prompt).build();
        DeepseekRequest requestBody = DeepseekRequest.builder()
                .model(model)
                .messages(Collections.singletonList(message))
                .temperature(0.7)
                .response_format(Collections.singletonMap("type", "json_object"))
                .build();

        Request request = new Request.Builder()
                .url(properties.getApiUrl())
                .post(RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json")))
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("API请求失败: " + response.code() + " - " + response.message());
            }
            return response.body().string();
        }
    }
}
