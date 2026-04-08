package com.app.service;

import com.app.dao.TextDAO;
import com.app.dto.AnalyzeTaskStatusDTO;
import com.app.dto.ExtractResumeItemDTO;
import com.app.dto.ResumeDTO;
import com.app.entity.TaskDO;
import com.app.entity.TextDO;
import com.app.service.repository.ResumeService;
import com.app.service.repository.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class DeepseekExtractService {

    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final int DEFAULT_BATCH_SIZE = 5;

    private final TaskService taskService;
    private final DeepseekBaseService deepseekBaseService;
    private final ResumeService resumeService;
    private final TextDAO textDAO;
    private final int llmQueueCapacity;
    private final int maxBatchSize;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ExecutorService llmExecutor;
    private final ConcurrentMap<String, AnalyzeTaskStatusDTO> statusStore = new ConcurrentHashMap<>();

    public DeepseekExtractService(TaskService taskService,
                                  DeepseekBaseService deepseekBaseService,
                                  ResumeService resumeService,
                                  TextDAO textDAO,
                                  @Value("${deepseek.extract.llm.executor-threads:5}") int llmThreads,
                                  @Value("${deepseek.extract.llm.queue-capacity:200}") int llmQueueCapacity,
                                  @Value("${deepseek.extract.batch.max-size:20}") int maxBatchSize) {
        this.taskService = taskService;
        this.deepseekBaseService = deepseekBaseService;
        this.resumeService = resumeService;
        this.textDAO = textDAO;
        int normalizedThreads = Math.max(1, llmThreads);
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
     * 提交 extract 任务
     */
    public String submitExtractTask(String taskId, String apiKey, boolean useSiliconFlow) {
        return submitExtractTask(taskId, apiKey, useSiliconFlow, DEFAULT_BATCH_SIZE);
    }

    /**
     * 提交 extract 任务，并指定单批并发处理简历数（默认 5）。
     */
    public String submitExtractTask(String taskId, String apiKey, boolean useSiliconFlow, Integer batchSize) {
        String existingTaskId = findActiveExtractTaskId(taskId);
        if (existingTaskId != null) {
            return existingTaskId;
        }

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

        List<ExtractResumeItemDTO> items = new ArrayList<>();
        for (TextDO row : rows) {
            if (row == null || row.getText() == null || row.getText().isBlank()) {
                continue;
            }
            ExtractResumeItemDTO item = new ExtractResumeItemDTO();
            item.setText(row.getText());
            if (row.getResumeId() != null && !row.getResumeId().isBlank()) {
                item.setBusinessResumeId(row.getResumeId().trim());
            }
            items.add(item);
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("task 下没有有效文本内容: " + taskId);
        }

        int parallelBatchSize = (batchSize == null || batchSize < 1) ? DEFAULT_BATCH_SIZE : batchSize;
        if (parallelBatchSize > maxBatchSize) {
            throw new IllegalArgumentException("batchSize 不能大于 " + maxBatchSize + "，当前: " + parallelBatchSize);
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

        executor.submit(() -> runExtractJob(analyzeTaskId, task, items, apiKey, useSiliconFlow, parallelBatchSize));
        return analyzeTaskId;
    }

    public AnalyzeTaskStatusDTO getExtractTaskStatus(String analyzeTaskId) {
        return statusStore.get(analyzeTaskId);
    }

    private String findActiveExtractTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        for (var entry : statusStore.entrySet()) {
            AnalyzeTaskStatusDTO status = entry.getValue();
            if (status == null) {
                continue;
            }
            if (!taskId.equals(status.getTaskId())) {
                continue;
            }
            String s = status.getStatus();
            if (STATUS_QUEUED.equalsIgnoreCase(s) || STATUS_RUNNING.equalsIgnoreCase(s)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void runExtractJob(String analyzeTaskId,
                               TaskDO task,
                               List<ExtractResumeItemDTO> items,
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
            int total = items.size();
            for (int start = 0; start < total; start += batchSize) {
                int end = Math.min(total, start + batchSize);
                // CompletableFuture意为这条异步任务未来会给你一个结果
                List<CompletableFuture<ExtractItemResult>> futures = new ArrayList<>(end - start);
                for (int i = start; i < end; i++) {
                    final int rankNo = i + 1;
                    final ExtractResumeItemDTO item = items.get(i);
                    futures.add(CompletableFuture.supplyAsync(
                            () -> processExtractItem(rankNo, item, apiKey, useSiliconFlow),
                            llmExecutor
                    ));
                }
                for (CompletableFuture<ExtractItemResult> future : futures) {
                    // 阻塞等待结果
                    ExtractItemResult result = future.join();
                    if (result.success) {
                        successCount++;
                    } else {
                        failedCount++;
                        if (!errorBuilder.isEmpty()) {
                            errorBuilder.append(" | ");
                        }
                        errorBuilder.append("rankNo=")
                                .append(result.rankNo)
                                .append(", error=")
                                .append(result.error == null ? "" : result.error);
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

    private ExtractItemResult processExtractItem(int rankNo,
                                                 ExtractResumeItemDTO item,
                                                 String apiKey,
                                                 boolean useSiliconFlow) {
        String text = item == null ? "" : item.getText();
        if (text == null || text.isBlank()) {
            return ExtractItemResult.failed(rankNo, "text 为空");
        }
        try {
            ResumeDTO resumeDTO = deepseekBaseService.generateResumeDetailFromText(apiKey, text, useSiliconFlow);
            String businessResumeId = item == null ? null : item.getBusinessResumeId();
            Long resumeId = resumeService.saveResumeAndReturnId(resumeDTO, businessResumeId);
            if (resumeId == null) {
                return ExtractItemResult.failed(rankNo, "saveResumeAndReturnId 返回空");
            }
            return ExtractItemResult.success(rankNo);
        } catch (IOException | RuntimeException e) {
            return ExtractItemResult.failed(rankNo, e.getMessage());
        }
    }

    private static class ExtractItemResult {
        private final int rankNo;
        private final boolean success;
        private final String error;

        private ExtractItemResult(int rankNo, boolean success, String error) {
            this.rankNo = rankNo;
            this.success = success;
            this.error = error;
        }

        static ExtractItemResult success(int rankNo) {
            return new ExtractItemResult(rankNo, true, null);
        }

        static ExtractItemResult failed(int rankNo, String error) {
            return new ExtractItemResult(rankNo, false, error);
        }
    }

    // 考虑后续更新更好的线程停止策略
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        llmExecutor.shutdown();
    }
}
