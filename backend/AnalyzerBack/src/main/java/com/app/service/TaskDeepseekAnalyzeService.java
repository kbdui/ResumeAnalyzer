package com.app.service;

import com.app.dto.AnalyzeTaskStatusDTO;
import com.app.dto.ResumeDTO;
import com.app.entity.TaskDO;
import com.app.entity.TaskResultDO;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TaskDeepseekAnalyzeService {

    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final TaskService taskService;
    private final TaskResultService taskResultService;
    private final DeepseekChatService deepseekChatService;
    private final ResumeService resumeService;
    private final TaskResumeMainService taskResumeMainService;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ConcurrentMap<String, AnalyzeTaskStatusDTO> statusStore = new ConcurrentHashMap<>();

    public TaskDeepseekAnalyzeService(TaskService taskService,
                                      TaskResultService taskResultService,
                                      DeepseekChatService deepseekChatService,
                                      ResumeService resumeService,
                                      TaskResumeMainService taskResumeMainService,
                                      ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.taskResultService = taskResultService;
        this.deepseekChatService = deepseekChatService;
        this.resumeService = resumeService;
        this.taskResumeMainService = taskResumeMainService;
        this.objectMapper = objectMapper;
    }

    public String submitAnalyzeTask(String taskId, String apiKey, boolean useSiliconFlow) {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task 不存在: " + taskId);
        }
        TaskResultDO taskResult = taskResultService.getByTaskId(task.getId());
        if (taskResult == null) {
            throw new IllegalArgumentException("task 还未产生筛选结果: " + taskId);
        }
        if (!STATUS_SUCCESS.equalsIgnoreCase(taskResult.getStatus())) {
            throw new IllegalArgumentException("task_result.status 未完成，当前状态: " + taskResult.getStatus());
        }

        JsonNode root = parseResultJson(taskResult.getResultJson());
        JsonNode items = root.path("result").path("results").path("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            throw new IllegalArgumentException("task_result 中未找到可分析的 items");
        }

        String analyzeTaskId = UUID.randomUUID().toString();
        AnalyzeTaskStatusDTO status = new AnalyzeTaskStatusDTO();
        status.setAnalyzeTaskId(analyzeTaskId);
        status.setTaskId(taskId);
        status.setStatus(STATUS_QUEUED);
        status.setTotal(items.size());
        status.setSuccessCount(0);
        status.setFailedCount(0);
        statusStore.put(analyzeTaskId, status);

        executor.submit(() -> runAnalyzeJob(analyzeTaskId, task, items, apiKey, useSiliconFlow));
        return analyzeTaskId;
    }

    public AnalyzeTaskStatusDTO getAnalyzeTaskStatus(String analyzeTaskId) {
        return statusStore.get(analyzeTaskId);
    }

    private void runAnalyzeJob(String analyzeTaskId,
                               TaskDO task,
                               JsonNode items,
                               String apiKey,
                               boolean useSiliconFlow) {
        AnalyzeTaskStatusDTO status = statusStore.get(analyzeTaskId);
        if (status == null) {
            return;
        }
        status.setStatus(STATUS_RUNNING);
        status.setStartedAtMs(System.currentTimeMillis());

        int successCount = 0;
        int failedCount = 0;
        StringBuilder errorBuilder = new StringBuilder();

        try {
            int rankNo = 0;
            for (JsonNode item : items) {
                rankNo++;
                String text = item.path("text").asText("");
                if (text == null || text.isBlank()) {
                    failedCount++;
                    continue;
                }
                try {
                    ResumeDTO resumeDTO = deepseekChatService.generateResumeDetailFromText(apiKey, text, useSiliconFlow);
                    Long resumeId = resumeService.saveResumeAndReturnId(resumeDTO);
                    if (resumeId == null) {
                        failedCount++;
                        continue;
                    }
                    JsonNode scoreNode = item.path("final_score");
                    BigDecimal finalScore = scoreNode.isMissingNode() || scoreNode.isNull()
                            ? null
                            : BigDecimal.valueOf(scoreNode.asDouble());
                    taskResumeMainService.saveRelation(
                            task.getId(),
                            resumeId,
                            rankNo,
                            finalScore,
                            analyzeTaskId
                    );
                    successCount++;
                } catch (IOException | RuntimeException e) {
                    failedCount++;
                    if (!errorBuilder.isEmpty()) {
                        errorBuilder.append(" | ");
                    }
                    errorBuilder.append("rankNo=")
                            .append(rankNo)
                            .append(", error=")
                            .append(e.getMessage());
                }
                status.setSuccessCount(successCount);
                status.setFailedCount(failedCount);
                status.setError(String.valueOf(errorBuilder));
            }
            if (successCount == items.size()) {
                status.setStatus(STATUS_SUCCESS);
            } else if (successCount == 0) {
                status.setStatus(STATUS_FAILED);
            } else {
                status.setStatus(STATUS_PARTIAL_SUCCESS);
            }
        } catch (RuntimeException e) {
            status.setStatus(STATUS_FAILED);
            status.setError(e.getMessage());
            status.setSuccessCount(successCount);
            status.setFailedCount(failedCount);
        } finally {
            status.setEndedAtMs(System.currentTimeMillis());
        }
    }

    private JsonNode parseResultJson(String resultJson) {
        try {
            return objectMapper.readTree(resultJson);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("task_result.result_json 解析失败: " + e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
