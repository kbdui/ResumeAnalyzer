package com.app.client;

import com.app.config.DeepseekClientProperties;
import com.app.request.DeepseekRequest;
import com.google.gson.Gson;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
public class DeepseekClient {

    private final OkHttpClient client;
    private final Gson gson = new Gson();
    private final DeepseekClientProperties properties;
    private final int maxRetries;
    private final long retryBackoffMs;
    private final long retryMaxBackoffMs;

    private static final Logger log = LoggerFactory.getLogger(DeepseekClient.class);

    public DeepseekClient(DeepseekClientProperties properties) {
        this.properties = properties;
        this.maxRetries = Math.max(0, properties.getRetry());
        this.retryBackoffMs = Math.max(100L, properties.getRetryBackoffMs());
        this.retryMaxBackoffMs = Math.max(this.retryBackoffMs, properties.getRetryMaxBackoffMs());

        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeout(), TimeUnit.SECONDS)
                .readTimeout(properties.getReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(properties.getWriteTimeout(), TimeUnit.SECONDS);
        if (properties.getCallTimeout() > 0) {
            builder.callTimeout(properties.getCallTimeout(), TimeUnit.SECONDS);
        }
        this.client = builder.build();
    }

    public String getResponse(String apiKey, String prompt) throws IOException {
        return doRequest(apiKey, prompt, properties.getModel().getV4flash(), "disabled", false);
    }

    public String getResponse(String apiKey, String prompt, boolean enablePro, boolean enableThinking) throws IOException {
        String model = enablePro ? properties.getModel().getV4pro() : properties.getModel().getV4flash();
        String thinking = enableThinking ? "enabled" : "disabled";
        return doRequest(apiKey, prompt, model, thinking, true);
    }

    private String doRequest(String apiKey, String prompt, String model, String thinkingType, boolean jsonMode) throws IOException {
        DeepseekRequest.Message message = DeepseekRequest.Message.builder()
                .role("user")
                .content(prompt)
                .build();
        DeepseekRequest.Thinking thinking = DeepseekRequest.Thinking.builder()
                .type(thinkingType)
                .build();

        DeepseekRequest.DeepseekRequestBuilder requestBuilder = DeepseekRequest.builder()
                .model(model)
                .messages(Collections.singletonList(message))
                // thinking options type cannot be disabled when reasoning_effort is set
                // .reasoningEffort("high")
                .thinking(thinking);

        if (jsonMode) {
            requestBuilder.response_format(Collections.singletonMap("type", "json_object"));
        }

        Request request = new Request.Builder()
                .url(properties.getApiUrl())
                .post(RequestBody.create(gson.toJson(requestBuilder.build()), MediaType.get("application/json")))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        int attempt = 0;
        int maxAttempts = maxRetries + 1;
        while (true) {
            attempt++;
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = response.body() == null ? "" : response.body().string();
                    String errMsg = "API请求失败: " + response.code() + " - " + response.message() + ", body=" + safeSnippet(errBody);
                    if (isRetryableHttpCode(response.code()) && attempt < maxAttempts) {
                        long sleepMs = computeBackoffWithJitter(attempt);
                        log.warn("HTTP状态可重试，attempt={}/{}, code={}, 将在{}ms后重试", attempt, maxAttempts, response.code(), sleepMs);
                        sleepQuietly(sleepMs);
                        continue;
                    }
                    throw new HttpStatusException(response.code(), errMsg);
                }

                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("response body is null");
                }
                String bodyStr = body.string();
                log.info("请求成功，attempt={}/{}", attempt, maxAttempts);
                return bodyStr;
            } catch (HttpStatusException e) {
                log.error("HTTP请求失败且不再重试，attempt={}/{}, code={}, error={}", attempt, maxAttempts, e.getStatusCode(), e.getMessage(), e);
                throw e;
            } catch (IOException e) {
                if (!isRetryableIo(e) || attempt >= maxAttempts) {
                    log.error("请求失败且不再重试，attempt={}/{}, error={}", attempt, maxAttempts, e.getMessage(), e);
                    throw e;
                }
                long sleepMs = computeBackoffWithJitter(attempt);
                log.warn("请求异常，attempt={}/{}, 将在{}ms后重试, error={}", attempt, maxAttempts, sleepMs, e.getMessage());
                sleepQuietly(sleepMs);
            }
        }
    }

    private boolean isRetryableHttpCode(int code) {
        return code == 429 || (code >= 500 && code <= 599);
    }

    private boolean isRetryableIo(IOException e) {
        return !(e instanceof InterruptedIOException);
    }

    private long computeBackoffWithJitter(int attempt) {
        long exp = retryBackoffMs * (1L << Math.max(0, attempt - 1));
        long capped = Math.min(exp, retryMaxBackoffMs);
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1L, capped / 4));
        return capped + jitter;
    }

    private String safeSnippet(String s) {
        if (s == null) {
            return "";
        }
        String clean = s.replaceAll("\\s+", " ").trim();
        return clean.length() <= 400 ? clean : clean.substring(0, 400) + "...";
    }

    private void sleepQuietly(long sleepMs) {
        try {
            Thread.sleep(Math.max(0L, sleepMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程中断", e);
        }
    }

    private static class HttpStatusException extends IOException {
        private final int statusCode;

        private HttpStatusException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        private int getStatusCode() {
            return statusCode;
        }
    }
}
