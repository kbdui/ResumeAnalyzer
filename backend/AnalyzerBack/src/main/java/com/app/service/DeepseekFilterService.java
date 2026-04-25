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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 按 JD 对 task 下简历异步批量调用大模型做三态硬过滤，结果写入 task_resume。
 */
@Service
public class DeepseekFilterService {

    private static final Logger log = LoggerFactory.getLogger(DeepseekFilterService.class);
    private static final Logger perfLog = LoggerFactory.getLogger("PERF_METRIC");
    private static final int[] PERF_CHECKPOINTS = {20, 50, 100, 200};
    private static final int DEFAULT_BATCH_SIZE = 5;

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
    private final int llmThreads;
    private final int llmQueueCapacity;
    private final int maxBatchSize;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ExecutorService llmExecutor;
    private final ConcurrentMap<String, AnalyzeTaskStatusDTO> statusStore = new ConcurrentHashMap<>();

    public DeepseekFilterService(TaskService taskService,
                                 TextDAO textDAO,
                                 ResumeService resumeService,
                                 TaskResumeDAO taskResumeDAO,
                                 DeepseekBaseService deepseekBaseService,
                                 ObjectMapper objectMapper,
                                 @Value("${deepseek.filter.llm.executor-threads:5}") int llmThreads,
                                 @Value("${deepseek.filter.llm.queue-capacity:200}") int llmQueueCapacity,
                                 @Value("${deepseek.filter.batch.max-size:20}") int maxBatchSize) {
        this.taskService = taskService;
        this.textDAO = textDAO;
        this.resumeService = resumeService;
        this.taskResumeDAO = taskResumeDAO;
        this.deepseekBaseService = deepseekBaseService;
        this.objectMapper = objectMapper;
        int normalizedThreads = Math.max(1, llmThreads);
        this.llmThreads = normalizedThreads;
        this.llmQueueCapacity = Math.max(1, llmQueueCapacity);
        this.maxBatchSize = Math.max(1, maxBatchSize);
        this.llmExecutor = new ThreadPoolExecutor(
                normalizedThreads,
                normalizedThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(this.llmQueueCapacity),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 提交异步硬过滤任务，供 GET /deepseek/hard-filter/{id} 轮询。
     */
    public String submitHardFilterTask(String businessTaskId,
                                       String jdText,
                                       String apiKey,
                                       boolean useSiliconFlow,
                                       Integer batchSize) {
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

        // 以 text 表中的业务 resume_id 为任务范围，去重后逐个从 resume 表加载结构化数据。
        LinkedHashSet<String> businessResumeIds = new LinkedHashSet<>();
        for (TextDO row : rows) {
            if (row == null || row.getResumeId() == null || row.getResumeId().isBlank()) {
                continue;
            }
            businessResumeIds.add(row.getResumeId().trim());
        }
        if (businessResumeIds.isEmpty()) {
            throw new IllegalArgumentException("task 下缺少可过滤的业务 resume_id: " + businessTaskId);
        }
        List<String> resumeIdList = new ArrayList<>(businessResumeIds);
        int parallelBatchSize = (batchSize == null || batchSize < 1) ? DEFAULT_BATCH_SIZE : batchSize;
        if (parallelBatchSize > maxBatchSize) {
            throw new IllegalArgumentException("batchSize 不能大于 " + maxBatchSize + "，当前: " + parallelBatchSize);
        }

        String filterTaskId = UUID.randomUUID().toString();
        AnalyzeTaskStatusDTO status = new AnalyzeTaskStatusDTO();
        status.setAnalyzeTaskId(filterTaskId);
        status.setTaskId(businessTaskId);
        status.setStatus(STATUS_QUEUED);
        status.setTotal(resumeIdList.size());
        status.setSuccessCount(0);
        status.setFailedCount(0);
        statusStore.put(filterTaskId, status);

        String jd = jdText.trim();
        executor.submit(() -> runHardFilterJob(
                filterTaskId,
                task,
                jd,
                resumeIdList,
                apiKey,
                useSiliconFlow,
                parallelBatchSize
        ));
        return filterTaskId;
    }

    public AnalyzeTaskStatusDTO getHardFilterTaskStatus(String filterTaskId) {
        return statusStore.get(filterTaskId);
    }

    private void runHardFilterJob(String filterTaskId,
                                  TaskDO task,
                                  String jdText,
                                  List<String> resumeIdList,
                                  String apiKey,
                                  boolean useSiliconFlow,
                                  int batchSize) {
        AnalyzeTaskStatusDTO status = statusStore.get(filterTaskId);
        if (status == null) {
            return;
        }
        long startedAtMs = System.currentTimeMillis();
        status.setStatus(STATUS_RUNNING);
        status.setStartedAtMs(startedAtMs);

        int successCount = 0;
        int failedCount = 0;
        StringBuilder errorBuilder = new StringBuilder();
        int total = resumeIdList.size();
        int effectiveParallelism = Math.max(1, Math.min(llmThreads, batchSize));
        boolean[] checkpointLogged = new boolean[PERF_CHECKPOINTS.length];

        perfLog.info(
                "PERF_HARD_FILTER_START businessTaskId={} filterTaskId={} total={} llmThreads={} batchSize={} effectiveParallelism={} queueCapacity={}",
                status.getTaskId(),
                filterTaskId,
                total,
                llmThreads,
                batchSize,
                effectiveParallelism,
                llmQueueCapacity
        );

        try {
            for (int start = 0; start < total; start += effectiveParallelism) {
                int end = Math.min(total, start + effectiveParallelism);
                List<CompletableFuture<FilterItemResult>> futures = new ArrayList<>(end - start);
                for (int i = start; i < end; i++) {
                    final int index = i + 1;
                    final String businessResumeId = resumeIdList.get(i);
                    futures.add(CompletableFuture.supplyAsync(
                            () -> processFilterItem(index, task.getId(), businessResumeId, jdText, apiKey, useSiliconFlow),
                            llmExecutor
                    ));
                }
                for (CompletableFuture<FilterItemResult> future : futures) {
                    FilterItemResult result = future.join();
                    if (result.success) {
                        successCount++;
                    } else {
                        failedCount++;
                        appendError(errorBuilder, result.index, result.error);
                    }
                    updateProgress(status, successCount, failedCount, errorBuilder);
                    logCheckpointIfNeeded(
                            "PERF_HARD_FILTER_CHECKPOINT",
                            status.getTaskId(),
                            filterTaskId,
                            startedAtMs,
                            total,
                            successCount,
                            failedCount,
                            llmThreads,
                            batchSize,
                            effectiveParallelism,
                            llmQueueCapacity,
                            checkpointLogged
                    );
                }
            }

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
            long endedAtMs = System.currentTimeMillis();
            long elapsedMs = Math.max(0L, endedAtMs - startedAtMs);
            int processedCount = successCount + failedCount;
            status.setEndedAtMs(endedAtMs);
            perfLog.info(
                    "PERF_HARD_FILTER_SUMMARY businessTaskId={} filterTaskId={} status={} total={} processed={} success={} failed={} llmThreads={} batchSize={} effectiveParallelism={} queueCapacity={} elapsedMs={} avgMsPerResume={} throughputPerMin={}",
                    status.getTaskId(),
                    filterTaskId,
                    status.getStatus(),
                    total,
                    processedCount,
                    successCount,
                    failedCount,
                    llmThreads,
                    batchSize,
                    effectiveParallelism,
                    llmQueueCapacity,
                    elapsedMs,
                    formatDecimal(processedCount == 0 ? 0D : (double) elapsedMs / processedCount),
                    formatDecimal(elapsedMs == 0 ? 0D : processedCount * 60000D / elapsedMs)
            );
        }
    }

    private FilterItemResult processFilterItem(int index,
                                               Long taskDbId,
                                               String businessResumeId,
                                               String jdText,
                                               String apiKey,
                                               boolean useSiliconFlow) {
        try {
            JsonNode resumePayload = buildResumePayloadFromResumeTable(businessResumeId);
            JsonNode analysis = deepseekBaseService.jdHardFilterAnalyze(apiKey, jdText, resumePayload, useSiliconFlow);
            boolean pass = computePassFromDimensions(analysis);
            String analysisJson = objectMapper.writeValueAsString(analysis);
            upsertResult(taskDbId, businessResumeId, pass, analysisJson);
            return FilterItemResult.success(index);
        } catch (Exception e) {
            upsertResult(taskDbId, businessResumeId, false, toErrorJson(e));
            return FilterItemResult.failed(index, e.getMessage());
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

    private JsonNode buildResumePayloadFromResumeTable(String businessResumeId) {
        ResumeDTO structured = resumeService.getResumeDetailByBusinessResumeId(businessResumeId);
        if (structured == null) {
            throw new IllegalArgumentException("resume 表中不存在业务 resume_id: " + businessResumeId);
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.put("source", "structured_resume");
        wrapper.put("resume_id", businessResumeId);
        wrapper.set("resume", objectMapper.valueToTree(structured));
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

    private static class FilterItemResult {
        private final int index;
        private final boolean success;
        private final String error;

        private FilterItemResult(int index, boolean success, String error) {
            this.index = index;
            this.success = success;
            this.error = error;
        }

        static FilterItemResult success(int index) {
            return new FilterItemResult(index, true, null);
        }

        static FilterItemResult failed(int index, String error) {
            return new FilterItemResult(index, false, error);
        }
    }

    private static String formatDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private void logCheckpointIfNeeded(String eventName,
                                       String businessTaskId,
                                       String filterTaskId,
                                       long startedAtMs,
                                       int total,
                                       int successCount,
                                       int failedCount,
                                       int llmThreads,
                                       int batchSize,
                                       int effectiveParallelism,
                                       int queueCapacity,
                                       boolean[] checkpointLogged) {
        int processedCount = successCount + failedCount;
        long now = System.currentTimeMillis();
        long elapsedMs = Math.max(0L, now - startedAtMs);
        for (int i = 0; i < PERF_CHECKPOINTS.length; i++) {
            int checkpoint = PERF_CHECKPOINTS[i];
            if (!checkpointLogged[i] && processedCount >= checkpoint) {
                checkpointLogged[i] = true;
                perfLog.info(
                        "{} businessTaskId={} filterTaskId={} checkpoint={} total={} processed={} success={} failed={} llmThreads={} batchSize={} effectiveParallelism={} queueCapacity={} elapsedMs={} avgMsPerResume={} throughputPerMin={}",
                        eventName,
                        businessTaskId,
                        filterTaskId,
                        checkpoint,
                        total,
                        processedCount,
                        successCount,
                        failedCount,
                        llmThreads,
                        batchSize,
                        effectiveParallelism,
                        queueCapacity,
                        elapsedMs,
                        formatDecimal(processedCount == 0 ? 0D : (double) elapsedMs / processedCount),
                        formatDecimal(elapsedMs == 0 ? 0D : processedCount * 60000D / elapsedMs)
                );
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        llmExecutor.shutdown();
    }
}
