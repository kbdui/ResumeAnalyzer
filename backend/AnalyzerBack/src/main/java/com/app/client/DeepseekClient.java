package com.app.client;

import com.app.config.DeepseekClientProperties;
import com.app.request.DeepseekRequest;
import com.app.service.DeepseekExtractService;
import com.google.gson.Gson;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class DeepseekClient {
    private final int MAX_RETRIES = 2;
    private final OkHttpClient client;
    private final Gson gson = new Gson();
    private final DeepseekClientProperties properties;

    private static final Logger log = LoggerFactory.getLogger(DeepseekExtractService.class);


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

        int attempt = 0;
        while (true) {
            try (Response response = client.newCall(request).execute()) {
                attempt++;

                if (!response.isSuccessful()) {
                    log.warn("请求失败，code={}, message={}", response.code(), response.message());
                    throw new IOException("API请求失败: " + response.code() + " - " + response.message());
                }

                ResponseBody body = response.body();
                if (body == null) {
                    throw new RuntimeException("response body is null");
                }

                String bodyStr = body.string();

                log.info("请求成功，第{}次尝试，response={}", attempt, bodyStr);

                return bodyStr;

            } catch (RuntimeException e) {
                // 只对 RuntimeException 重试
                if (attempt > MAX_RETRIES) {
                    log.error("请求失败，重试后仍异常: {}", e.getMessage(), e);
                    throw e;
                }

                log.warn("RuntimeException，第{}次重试: {}", attempt, e.getMessage(), e);

                sleepQuietly();

            }
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程中断", e);
        }
    }
}
