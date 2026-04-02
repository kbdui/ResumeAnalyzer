package com.app.service;

import com.app.dao.TaskResumeDAO;
import com.app.dao.TextDAO;
import com.app.dto.AnalyzeTaskStatusDTO;
import com.app.dto.ResumeDTO;
import com.app.entity.TaskDO;
import com.app.entity.TaskResumeDO;
import com.app.entity.TextDO;
import com.app.service.repository.ResumeService;
import com.app.service.repository.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 按 JD 对 task 下简历异步批量调用大模型做三态硬过滤，结果写入 task_resume。
 */
@Service
public class ResumeFilterService {

    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private static final String[] DIMENSION_KEYS = {"education", "work_experience", "skills", "projects"};

    private final TaskService taskService;
    private final TextDAO textDAO;
    private final ResumeService resumeService;
    private final TaskResumeDAO taskResumeDAO;
    private final DeepseekBaseService deepseekBaseService;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ConcurrentMap<String, AnalyzeTaskStatusDTO> statusStore = new ConcurrentHashMap<>();

    public ResumeFilterService(TaskService taskService,
                               TextDAO textDAO,
                               ResumeService resumeService,
                               TaskResumeDAO taskResumeDAO,
                               DeepseekBaseService deepseekBaseService,
                               ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.textDAO = textDAO;
        this.resumeService = resumeService;
        this.taskResumeDAO = taskResumeDAO;
        this.deepseekBaseService = deepseekBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交异步硬过滤任务，返回与深度分析相同的 jobId，供 GET /deepseek/analyze/{id} 轮询。
     */
    public String submitHardFilterTask(String businessTaskId, String jdText, String apiKey, boolean useSiliconFlow) {
        if (jdText == null || jdText.isBlank()) {
            throw new IllegalArgumentException("jdText 不能为空");
        }
        TaskDO task = taskService.getByBusinessTaskId(businessTaskId);
        if (task == null) {
            throw new IllegalArgumentException("task 不存在: " + businessTaskId);
        }
        List<TextDO> rows = textDAO.selectList(new LambdaQueryWrapper<TextDO>()
                .eq(TextDO::getTaskId, task.getId())
                .orderByAsc(TextDO::getId));
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("task 下没有可过滤的文本: " + businessTaskId);
        }

        String filterTaskId = UUID.randomUUID().toString();
        AnalyzeTaskStatusDTO status = new AnalyzeTaskStatusDTO();
        status.setAnalyzeTaskId(filterTaskId);
        status.setTaskId(businessTaskId);
        status.setStatus(STATUS_QUEUED);
        status.setTotal(rows.size());
        status.setSuccessCount(0);
        status.setFailedCount(0);
        statusStore.put(filterTaskId, status);

        String jd = jdText.trim();
        executor.submit(() -> runHardFilterJob(filterTaskId, task, jd, rows, apiKey, useSiliconFlow));
        return filterTaskId;
    }

    public AnalyzeTaskStatusDTO getHardFilterTaskStatus(String filterTaskId) {
        return statusStore.get(filterTaskId);
    }

    private void runHardFilterJob(String filterTaskId,
                                  TaskDO task,
                                  String jdText,
                                  List<TextDO> rows,
                                  String apiKey,
                                  boolean useSiliconFlow) {
        AnalyzeTaskStatusDTO status = statusStore.get(filterTaskId);
        if (status == null) {
            return;
        }
        status.setStatus(STATUS_RUNNING);
        status.setStartedAtMs(System.currentTimeMillis());

        int successCount = 0;
        int failedCount = 0;
        StringBuilder errorBuilder = new StringBuilder();

        try {
            int index = 0;
            for (TextDO textRow : rows) {
                index++;
                if (textRow == null || textRow.getResumeId() == null || textRow.getResumeId().isBlank()) {
                    failedCount++;
                    appendError(errorBuilder, index, "缺少业务 resume_id");
                    upsertErrorJson(task.getId(), textRow != null ? textRow.getResumeId() : null,
                            "{\"error\":\"missing_resume_id\",\"message\":\"text 行缺少 resume_id\"}");
                    updateProgress(status, successCount, failedCount, errorBuilder);
                    continue;
                }
                String businessResumeId = textRow.getResumeId().trim();
                try {
                    JsonNode resumePayload = buildResumePayload(textRow);
                    JsonNode analysis = deepseekBaseService.jdHardFilterAnalyze(apiKey, jdText, resumePayload, useSiliconFlow);
                    boolean pass = computePassFromDimensions(analysis);
                    String analysisJson = objectMapper.writeValueAsString(analysis);
                    upsertResult(task.getId(), businessResumeId, pass, analysisJson);
                    successCount++;
                } catch (Exception e) {
                    failedCount++;
                    appendError(errorBuilder, index, e.getMessage());
                    upsertResult(task.getId(), businessResumeId, false, toErrorJson(e));
                }
                updateProgress(status, successCount, failedCount, errorBuilder);
            }

            int total = rows.size();
            if (successCount == total) {
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

    private static void updateProgress(AnalyzeTaskStatusDTO status, int successCount, int failedCount, StringBuilder errorBuilder) {
        status.setSuccessCount(successCount);
        status.setFailedCount(failedCount);
        status.setError(String.valueOf(errorBuilder));
    }

    private static void appendError(StringBuilder errorBuilder, int index, String message) {
        if (!errorBuilder.isEmpty()) {
            errorBuilder.append(" | ");
        }
        errorBuilder.append("index=").append(index).append(", error=").append(message != null ? message : "");
    }

    private JsonNode buildResumePayload(TextDO textRow) {
        ResumeDTO structured = resumeService.getResumeDetailByBusinessResumeId(textRow.getResumeId());
        if (structured != null) {
            return objectMapper.valueToTree(structured);
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("source", "raw_text_only");
        wrapper.put("resume_id", textRow.getResumeId());
        wrapper.put("file_name", textRow.getFileName() != null ? textRow.getFileName() : "");
        wrapper.put("raw_text", textRow.getText() != null ? textRow.getText() : "");
        return wrapper;
    }

    /**
     * 任一项 status 为 FAIL 则不通过；缺失维度或 UNKNOWN 不单独判失败。
     */
    static boolean computePassFromDimensions(JsonNode root) {
        if (root == null || !root.isObject()) {
            return true;
        }
        for (String key : DIMENSION_KEYS) {
            JsonNode dim = root.path(key);
            if (dim == null || dim.isMissingNode() || dim.isNull()) {
                continue;
            }
            String s = dim.path("status").asText("UNKNOWN").trim();
            if ("FAIL".equalsIgnoreCase(s)) {
                return false;
            }
        }
        return true;
    }

    private String toErrorJson(Exception e) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("error", e.getClass().getSimpleName());
        n.put("message", e.getMessage() != null ? e.getMessage() : "");
        try {
            return objectMapper.writeValueAsString(n);
        } catch (JacksonException ex) {
            return "{\"error\":\"serialize_failed\"}";
        }
    }

    void upsertResult(Long taskDbId, String businessResumeId, boolean pass, String analysisJson) {
        TaskResumeDO existing = taskResumeDAO.selectOne(new LambdaQueryWrapper<TaskResumeDO>()
                .eq(TaskResumeDO::getTaskId, taskDbId)
                .eq(TaskResumeDO::getResumeId, businessResumeId)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            TaskResumeDO row = new TaskResumeDO();
            row.setTaskId(taskDbId);
            row.setResumeId(businessResumeId);
            row.setPass(pass);
            row.setAnalysisJson(analysisJson);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            taskResumeDAO.insert(row);
        } else {
            existing.setPass(pass);
            existing.setAnalysisJson(analysisJson);
            existing.setUpdateTime(now);
            taskResumeDAO.updateById(existing);
        }
    }

    private void upsertErrorJson(Long taskDbId, String businessResumeId, String analysisJson) {
        if (businessResumeId == null || businessResumeId.isBlank()) {
            return;
        }
        upsertResult(taskDbId, businessResumeId.trim(), false, analysisJson);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
