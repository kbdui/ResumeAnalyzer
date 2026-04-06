package com.app.web;

import com.app.dto.ResumeDTO;
import com.app.dto.AnalyzeSubmitResponseDTO;
import com.app.dto.AnalyzeTaskStatusDTO;
import com.app.entity.AnalysisDO;
import com.app.request.ChatRequest;
import com.app.request.JdHardFilterRequest;
import com.app.service.DeepseekBaseService;
import com.app.service.repository.ResumeService;
import com.app.service.DeepseekAnalyzeService;
import com.app.service.DeepseekExtractService;
import com.app.service.ResumeFilterService;
import com.app.tool.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/deepseek")
@CrossOrigin
public class DeepseekController {
    @Value("${deepseek.api.key}")
    private String apiKey;

    private final DeepseekBaseService deepseekBaseService;
    private final ResumeService resumeService;
    private final DeepseekAnalyzeService deepseekAnalyzeService;
    private final DeepseekExtractService deepseekExtractService;
    private final ResumeFilterService resumeFilterService;

    @Autowired
    public DeepseekController(DeepseekBaseService deepseekBaseService,
                              ResumeService resumeService,
                              DeepseekAnalyzeService deepseekAnalyzeService,
                              DeepseekExtractService deepseekExtractService,
                              ResumeFilterService resumeFilterService) {
        this.deepseekBaseService = deepseekBaseService;
        this.resumeService = resumeService;
        this.deepseekAnalyzeService = deepseekAnalyzeService;
        this.deepseekExtractService = deepseekExtractService;
        this.resumeFilterService = resumeFilterService;
    }

    /**
     * 简单对话：发送问题，返回大模型回复
     * 请求体 JSON: { "message": "你的问题" }
     */
    @PostMapping("/chat")
    public ApiResponse<String> chat(@RequestBody ChatRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            return ApiResponse.error(400, "message 不能为空");
        }
        try {
            String reply = deepseekBaseService.chat(apiKey, request.getMessage());
            return ApiResponse.success(reply);
        } catch (IOException e) {
            return ApiResponse.error(500, "调用失败: " + e.getMessage());
        }
    }

    /**
     * 整理分析上传简历内的信息
     */
    @PostMapping("/extract")
    public ApiResponse<ResumeDTO> extractResumeDetail(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "model", defaultValue = "v3") String model,
            @RequestParam(value = "save", defaultValue = "false") boolean save) {
        try {
            ResumeDTO result = deepseekBaseService.generateResumeDetailFromFile(apiKey, file, Objects.equals(model, "v3"));
            if (save) {
                resumeService.saveResume(result);
            }
            return ApiResponse.success(result);
        } catch (IOException e) {
            return ApiResponse.error(500, "简历分析失败: " + e.getMessage());
        }
    }

    /**
     * 提交 task 下筛选简历给大模型进行异步批量分析
     */
    @PostMapping("/{taskId}/extract")
    public ApiResponse<AnalyzeSubmitResponseDTO> submitExtractTask(
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "model", defaultValue = "v3") String model,
            @RequestParam(value = "batchSize", defaultValue = "5") Integer batchSize) {
        if (taskId == null || taskId.isBlank()) {
            return ApiResponse.error(400, "taskId 不能为空");
        }
        if (batchSize == null || batchSize < 1) {
            return ApiResponse.error(400, "batchSize 必须 >= 1");
        }
        try {
            String analyzeTaskId = deepseekExtractService.submitExtractTask(
                    taskId,
                    apiKey,
                    Objects.equals(model, "v3"),
                    batchSize
            );
            AnalyzeSubmitResponseDTO response = new AnalyzeSubmitResponseDTO();
            response.setAnalyzeTaskId(analyzeTaskId);
            response.setTaskId(taskId);
            response.setMessage("提交成功");
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 查询分析任务进度
     */
    @GetMapping("/analyze/{analyzeTaskId}")
    public ApiResponse<AnalyzeTaskStatusDTO> getExtractTaskStatus(@PathVariable("analyzeTaskId") String analyzeTaskId) {
        AnalyzeTaskStatusDTO status = deepseekAnalyzeService.getAnalyzeTaskStatus(analyzeTaskId);
        if (status == null) {
            status = deepseekExtractService.getExtractTaskStatus(analyzeTaskId);
        }
        if (status == null) {
            status = resumeFilterService.getHardFilterTaskStatus(analyzeTaskId);
        }
        if (status == null) {
            return ApiResponse.error(404, "任务不存在: " + analyzeTaskId);
        }
        return ApiResponse.success(status);
    }

    /**
     * 将 hybrid_result 提交给 LLM 做最终评估分析，并入库 analysis 表。
     */
    @PostMapping("/{taskId}/analyze")
    public ApiResponse<AnalyzeSubmitResponseDTO> submitFinalAnalyzeTask(
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "model", defaultValue = "v3") String model,
            @RequestParam(value = "batchSize", defaultValue = "5") Integer batchSize) {
        if (taskId == null || taskId.isBlank()) {
            return ApiResponse.error(400, "taskId 不能为空");
        }
        if (batchSize == null || batchSize < 1) {
            return ApiResponse.error(400, "batchSize 必须 >= 1");
        }
        try {
            String analyzeTaskId = deepseekAnalyzeService.submitAnalyzeTask(
                    taskId,
                    apiKey,
                    Objects.equals(model, "v3"),
                    batchSize
            );
            AnalyzeSubmitResponseDTO response = new AnalyzeSubmitResponseDTO();
            response.setAnalyzeTaskId(analyzeTaskId);
            response.setTaskId(taskId);
            response.setMessage("最终评估任务已提交");
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 异步提交 JD 硬过滤
     */
    @PostMapping("/{taskId}/hard-filter")
    public ApiResponse<AnalyzeSubmitResponseDTO> submitHardFilterByJd(
            @PathVariable("taskId") String taskId,
            @RequestBody JdHardFilterRequest body,
            @RequestParam(value = "model", defaultValue = "v3") String model) {
        if (taskId == null || taskId.isBlank()) {
            return ApiResponse.error(400, "taskId 不能为空");
        }
        if (body == null || body.getJdText() == null || body.getJdText().isBlank()) {
            return ApiResponse.error(400, "jdText 不能为空");
        }
        try {
            String filterTaskId = resumeFilterService.submitHardFilterTask(
                    taskId,
                    body.getJdText().trim(),
                    apiKey,
                    Objects.equals(model, "v3"));
            AnalyzeSubmitResponseDTO response = new AnalyzeSubmitResponseDTO();
            response.setAnalyzeTaskId(filterTaskId);
            response.setTaskId(taskId);
            response.setMessage("硬过滤任务已提交");
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 查询 JD 硬过滤任务进度
     */
    @GetMapping("/hard-filter/{hardFilterTaskId}")
    public ApiResponse<AnalyzeTaskStatusDTO> getHardFilterTaskStatus(@PathVariable("hardFilterTaskId") String hardFilterTaskId) {
        if (hardFilterTaskId == null || hardFilterTaskId.isBlank()) {
            return ApiResponse.error(400, "hardFilterTaskId 不能为空");
        }
        AnalyzeTaskStatusDTO status = resumeFilterService.getHardFilterTaskStatus(hardFilterTaskId);
        if (status == null) {
            return ApiResponse.error(404, "硬过滤任务不存在: " + hardFilterTaskId);
        }
        return ApiResponse.success(status);
    }

    /**
     * 查询 task 下最终评估列表（analysis 表）。
     */
    @GetMapping("/{taskId}/analysis")
    public ApiResponse<List<AnalysisDO>> listAnalysisByTaskId(@PathVariable("taskId") String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return ApiResponse.error(400, "taskId 不能为空");
        }
        try {
            List<AnalysisDO> rows = deepseekAnalyzeService.listAnalysisByBusinessTaskId(taskId);
            return ApiResponse.success(rows);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
