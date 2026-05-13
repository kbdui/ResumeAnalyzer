package com.app.service;

import com.app.dao.AnalysisDAO;
import com.app.dto.AnalyzeTaskStatusDTO;
import com.app.entity.AnalysisDO;
import com.app.entity.HybridResultDO;
import com.app.entity.JdExtractDO;
import com.app.entity.TaskDO;
import com.app.service.repository.HybridResultService;
import com.app.service.repository.JdExtractService;
import com.app.service.repository.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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

@Service
public class DeepseekAnalyzeService {

    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final int DEFAULT_BATCH_SIZE = 5;

    private final TaskService taskService;
    private final HybridResultService hybridResultService;
    private final JdExtractService jdExtractService;
    private final AnalysisDAO analysisDAO;
    private final DeepseekBaseService deepseekBaseService;
    private final ObjectMapper objectMapper;
    private final int maxBatchSize;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ExecutorService llmExecutor;
    private final ConcurrentMap<String, AnalyzeTaskStatusDTO> statusStore = new ConcurrentHashMap<>();

    public DeepseekAnalyzeService(TaskService taskService,
                                  HybridResultService hybridResultService,
                                  JdExtractService jdExtractService,
                                  AnalysisDAO analysisDAO,
                                  DeepseekBaseService deepseekBaseService,
                                  ObjectMapper objectMapper,
                                  @Value("${deepseek.analyze.llm.executor-threads:5}") int llmThreads,
                                  @Value("${deepseek.analyze.llm.queue-capacity:200}") int llmQueueCapacity,
                                  @Value("${deepseek.analyze.batch.max-size:20}") int maxBatchSize) {
        this.taskService = taskService;
        this.hybridResultService = hybridResultService;
        this.jdExtractService = jdExtractService;
        this.analysisDAO = analysisDAO;
        this.deepseekBaseService = deepseekBaseService;
        this.objectMapper = objectMapper;
        this.maxBatchSize = Math.max(1, maxBatchSize);
        int normalizedThreads = Math.max(1, llmThreads);
        int normalizedQueue = Math.max(1, llmQueueCapacity);
        this.llmExecutor = new ThreadPoolExecutor(
                normalizedThreads,
                normalizedThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(normalizedQueue),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public String submitAnalyzeTask(String taskId, String apiKey, boolean useSiliconFlow) {
        return submitAnalyzeTask(taskId, apiKey, useSiliconFlow, DEFAULT_BATCH_SIZE, null);
    }

    public String submitAnalyzeTask(String taskId, String apiKey, boolean useSiliconFlow, Integer batchSize) {
        return submitAnalyzeTask(taskId, apiKey, useSiliconFlow, batchSize, null);
    }

    public String submitAnalyzeTask(String taskId, String apiKey, boolean useSiliconFlow, Integer batchSize, Integer analyzeCount) {
        String existingTaskId = findActiveAnalyzeTaskId(taskId);
        if (existingTaskId != null) {
            return existingTaskId;
        }

        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task 不存在: " + taskId);
        }
        HybridResultDO hybridResult = hybridResultService.getByTaskId(task.getId());
        if (hybridResult == null || hybridResult.getResultJson() == null || hybridResult.getResultJson().isBlank()) {
            throw new IllegalArgumentException("task 下没有可用于评估的 hybrid_result: " + taskId);
        }
        JdExtractDO jdExtract = jdExtractService.getByTaskId(task.getId());
        if (jdExtract == null || jdExtract.getJdText() == null || jdExtract.getJdText().isBlank()) {
            throw new IllegalArgumentException("task 下没有 JD 信息，请先完成 hybrid 提交（jd_extract）: " + taskId);
        }

        List<JsonNode> items = parseHybridItems(hybridResult.getResultJson());
        if (items.isEmpty()) {
            throw new IllegalArgumentException("hybrid_result 中未找到可分析的 items");
        }

        int normalizedAnalyzeCount = analyzeCount == null ? items.size() : analyzeCount;
        if (normalizedAnalyzeCount < 1) {
            throw new IllegalArgumentException("analyzeCount 必须 >= 1");
        }
        if (normalizedAnalyzeCount > items.size()) {
            throw new IllegalArgumentException("analyzeCount 不能大于通过召回筛选的简历数量，当前最多: " + items.size());
        }
        List<JsonNode> itemsToAnalyze = new ArrayList<>(items.subList(0, normalizedAnalyzeCount));

        int parallelBatchSize = (batchSize == null || batchSize < 1) ? DEFAULT_BATCH_SIZE : batchSize;
        if (parallelBatchSize > maxBatchSize) {
            throw new IllegalArgumentException("batchSize 不能大于 " + maxBatchSize + "，当前: " + parallelBatchSize);
        }

        String analyzeTaskId = UUID.randomUUID().toString();
        AnalyzeTaskStatusDTO status = new AnalyzeTaskStatusDTO();
        status.setAnalyzeTaskId(analyzeTaskId);
        status.setTaskId(taskId);
        status.setStatus(STATUS_QUEUED);
        status.setTotal(itemsToAnalyze.size());
        status.setSuccessCount(0);
        status.setFailedCount(0);
        statusStore.put(analyzeTaskId, status);

        executor.submit(() -> runAnalyzeJob(
                analyzeTaskId,
                task,
                jdExtract.getJdText(),
                itemsToAnalyze,
                apiKey,
                useSiliconFlow,
                parallelBatchSize
        ));
        return analyzeTaskId;
    }

    public AnalyzeTaskStatusDTO getAnalyzeTaskStatus(String analyzeTaskId) {
        return statusStore.get(analyzeTaskId);
    }

    public List<AnalysisDO> listAnalysisByBusinessTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        TaskDO task = taskService.getByBusinessTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task 不存在: " + taskId);
        }
        List<AnalysisDO> rows = analysisDAO.selectList(new LambdaQueryWrapper<AnalysisDO>()
                .eq(AnalysisDO::getTaskId, task.getId())
                .orderByDesc(AnalysisDO::getUpdateTime)
                .orderByDesc(AnalysisDO::getId));
        return rows == null ? Collections.emptyList() : rows;
    }

    private void runAnalyzeJob(String analyzeTaskId,
                               TaskDO task,
                               String jdText,
                               List<JsonNode> items,
                               String apiKey,
                               boolean useSiliconFlow,
                               int batchSize) {
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
            // 每次重新分析前先清空当前 task 的历史结果，避免旧简历残留。
            analysisDAO.delete(new LambdaQueryWrapper<AnalysisDO>()
                    .eq(AnalysisDO::getTaskId, task.getId()));

            int total = items.size();
            for (int start = 0; start < total; start += batchSize) {
                int end = Math.min(total, start + batchSize);
                List<CompletableFuture<AnalyzeItemResult>> futures = new ArrayList<>(end - start);
                for (int i = start; i < end; i++) {
                    final int rankNo = i + 1;
                    final JsonNode item = items.get(i);
                    futures.add(CompletableFuture.supplyAsync(
                            () -> processAnalyzeItem(rankNo, task.getId(), jdText, item, apiKey, useSiliconFlow),
                            llmExecutor
                    ));
                }
                for (CompletableFuture<AnalyzeItemResult> future : futures) {
                    AnalyzeItemResult r = future.join();
                    if (r.success) {
                        successCount++;
                    } else {
                        failedCount++;
                        if (errorBuilder.length() > 0) {
                            errorBuilder.append(" | ");
                        }
                        errorBuilder.append("rankNo=").append(r.rankNo)
                                .append(", error=")
                                .append(r.error == null ? "" : r.error);
                    }
                    status.setSuccessCount(successCount);
                    status.setFailedCount(failedCount);
                    status.setError(String.valueOf(errorBuilder));
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
            status.setEndedAtMs(System.currentTimeMillis());
        }
    }

    private AnalyzeItemResult processAnalyzeItem(int rankNo,
                                                 Long taskDbId,
                                                 String jdText,
                                                 JsonNode item,
                                                 String apiKey,
                                                 boolean useSiliconFlow) {
        String resumeId = item.path("resume_id").asText("").trim();
        if (resumeId.isBlank()) {
            return AnalyzeItemResult.failed(rankNo, "resume_id 为空");
        }
        try {
            JsonNode analysis = deepseekBaseService.analyzeHybridResume(apiKey, jdText, item, useSiliconFlow);
            String analysisJson = objectMapper.writeValueAsString(analysis);
            upsertAnalysis(taskDbId, resumeId, analysisJson);
            return AnalyzeItemResult.success(rankNo);
        } catch (IOException | RuntimeException e) {
            return AnalyzeItemResult.failed(rankNo, e.getMessage());
        }
    }

    private List<JsonNode> parseHybridItems(String resultJson) {
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            JsonNode itemsNode = root.path("result").path("results").path("items");
            if (!itemsNode.isArray() || itemsNode.isEmpty()) {
                return List.of();
            }
            List<JsonNode> items = new ArrayList<>(itemsNode.size());
            for (JsonNode item : itemsNode) {
                if (item != null && !item.isNull()) {
                    items.add(item);
                }
            }
            return items;
        } catch (Exception e) {
            throw new IllegalArgumentException("hybrid_result.result_json 解析失败: " + e.getMessage());
        }
    }

    private String findActiveAnalyzeTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        for (var entry : statusStore.entrySet()) {
            AnalyzeTaskStatusDTO status = entry.getValue();
            if (status == null || !taskId.equals(status.getTaskId())) {
                continue;
            }
            String s = status.getStatus();
            if (STATUS_QUEUED.equalsIgnoreCase(s) || STATUS_RUNNING.equalsIgnoreCase(s)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void upsertAnalysis(Long taskDbId, String resumeId, String analysisJson) {
        AnalysisDO existing = analysisDAO.selectOne(new LambdaQueryWrapper<AnalysisDO>()
                .eq(AnalysisDO::getTaskId, taskDbId)
                .eq(AnalysisDO::getResumeId, resumeId)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            AnalysisDO row = new AnalysisDO();
            row.setTaskId(taskDbId);
            row.setResumeId(resumeId);
            row.setAnalysisJson(analysisJson);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            analysisDAO.insert(row);
        } else {
            AnalysisDO update = new AnalysisDO();
            update.setId(existing.getId());
            update.setAnalysisJson(analysisJson);
            update.setUpdateTime(now);
            analysisDAO.updateById(update);
        }
    }

    private static class AnalyzeItemResult {
        private final int rankNo;
        private final boolean success;
        private final String error;

        private AnalyzeItemResult(int rankNo, boolean success, String error) {
            this.rankNo = rankNo;
            this.success = success;
            this.error = error;
        }

        static AnalyzeItemResult success(int rankNo) {
            return new AnalyzeItemResult(rankNo, true, null);
        }

        static AnalyzeItemResult failed(int rankNo, String error) {
            return new AnalyzeItemResult(rankNo, false, error);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        llmExecutor.shutdown();
    }
}
