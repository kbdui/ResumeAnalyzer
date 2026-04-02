package com.app.service;

import com.app.dao.TextDAO;
import com.app.dto.AnalyzeTaskStatusDTO;
import com.app.dto.ResumeDTO;
import com.app.entity.TaskDO;
import com.app.entity.TextDO;
import com.app.service.repository.ResumeService;
import com.app.service.repository.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DeepseekExtractService {

    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final TaskService taskService;
    private final DeepseekBaseService deepseekBaseService;
    private final ResumeService resumeService;
    private final TextDAO textDAO;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ConcurrentMap<String, AnalyzeTaskStatusDTO> statusStore = new ConcurrentHashMap<>();

    public DeepseekExtractService(TaskService taskService,
                                  DeepseekBaseService deepseekBaseService,
                                  ResumeService resumeService,
                                  TextDAO textDAO,
                                  ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.deepseekBaseService = deepseekBaseService;
        this.resumeService = resumeService;
        this.textDAO = textDAO;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交 extract 任务
     */
    public String submitExtractTask(String taskId, String apiKey, boolean useSiliconFlow) {
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task 不存在: " + taskId);
        }
        List<TextDO> rows = textDAO.selectList(new LambdaQueryWrapper<TextDO>()
                .eq(TextDO::getTaskId, task.getId())
                .orderByAsc(TextDO::getId));
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("task 下没有可分析的文本: " + taskId);
        }

        ArrayNode items = objectMapper.createArrayNode();
        for (TextDO row : rows) {
            if (row == null || row.getText() == null || row.getText().isBlank()) {
                continue;
            }
            ObjectNode o = objectMapper.createObjectNode();
            o.put("text", row.getText());
            if (row.getResumeId() != null && !row.getResumeId().isBlank()) {
                o.put("business_resume_id", row.getResumeId());
            }
            items.add(o);
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("task 下没有有效文本内容: " + taskId);
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

        executor.submit(() -> runExtractJob(analyzeTaskId, task, items, apiKey, useSiliconFlow));
        return analyzeTaskId;
    }

    public AnalyzeTaskStatusDTO getExtractTaskStatus(String analyzeTaskId) {
        return statusStore.get(analyzeTaskId);
    }

    private void runExtractJob(String analyzeTaskId,
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
                    ResumeDTO resumeDTO = deepseekBaseService.generateResumeDetailFromText(apiKey, text, useSiliconFlow);
                    String businessResumeId = item.path("business_resume_id").asText("");
                    if (businessResumeId.isBlank()) {
                        businessResumeId = null;
                    }
                    Long resumeId = resumeService.saveResumeAndReturnId(resumeDTO, businessResumeId);
                    if (resumeId == null) {
                        failedCount++;
                        continue;
                    }
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

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
