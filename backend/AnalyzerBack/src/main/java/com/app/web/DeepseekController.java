package com.app.web;

import com.app.dto.AnalyzeSubmitResponseDTO;
import com.app.dto.AnalyzeTaskStatusDTO;
import com.app.dto.ResumeDTO;
import com.app.request.ChatRequest;
import com.app.request.JdHardFilterRequest;
import com.app.service.DeepseekAnalyzeService;
import com.app.service.DeepseekBaseService;
import com.app.service.DeepseekExtractService;
import com.app.service.DeepseekFilterService;
import com.app.service.repository.ResumeService;
import com.app.tool.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final DeepseekFilterService deepseekFilterService;

    @Autowired
    public DeepseekController(DeepseekBaseService deepseekBaseService,
                              ResumeService resumeService,
                              DeepseekAnalyzeService deepseekAnalyzeService,
                              DeepseekExtractService deepseekExtractService,
                              DeepseekFilterService deepseekFilterService) {
        this.deepseekBaseService = deepseekBaseService;
        this.resumeService = resumeService;
        this.deepseekAnalyzeService = deepseekAnalyzeService;
        this.deepseekExtractService = deepseekExtractService;
        this.deepseekFilterService = deepseekFilterService;
    }

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
            return ApiResponse.error(500, "简历解析失败: " + e.getMessage());
        }
    }

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
            response.setMessage("提取任务已提交");
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/{taskId}/hard-filter")
    public ApiResponse<AnalyzeSubmitResponseDTO> submitHardFilterByJd(
            @PathVariable("taskId") String taskId,
            @RequestBody JdHardFilterRequest body,
            @RequestParam(value = "model", defaultValue = "v3") String model,
            @RequestParam(value = "batchSize", defaultValue = "5") Integer batchSize) {
        if (taskId == null || taskId.isBlank()) {
            return ApiResponse.error(400, "taskId 不能为空");
        }
        if (body == null || body.getJdText() == null || body.getJdText().isBlank()) {
            return ApiResponse.error(400, "jdText 不能为空");
        }
        if (batchSize == null || batchSize < 1) {
            return ApiResponse.error(400, "batchSize 必须 >= 1");
        }
        try {
            String filterTaskId = deepseekFilterService.submitHardFilterTask(
                    taskId,
                    body.getJdText().trim(),
                    apiKey,
                    Objects.equals(model, "v3"),
                    batchSize
            );
            AnalyzeSubmitResponseDTO response = new AnalyzeSubmitResponseDTO();
            response.setAnalyzeTaskId(filterTaskId);
            response.setTaskId(taskId);
            response.setMessage("硬过滤任务已提交");
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

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
            response.setMessage("评估任务已提交");
            return ApiResponse.success(response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/extract/{extractTaskId}")
    public ApiResponse<AnalyzeTaskStatusDTO> getExtractTaskStatus(@PathVariable("extractTaskId") String extractTaskId) {
        AnalyzeTaskStatusDTO status = deepseekExtractService.getExtractTaskStatus(extractTaskId);
        if (status == null) {
            return ApiResponse.error(404, "extract任务不存在: " + extractTaskId);
        }
        return ApiResponse.success(status);
    }

    @GetMapping("/hard-filter/{hardFilterTaskId}")
    public ApiResponse<AnalyzeTaskStatusDTO> getHardFilterTaskStatus(@PathVariable("hardFilterTaskId") String hardFilterTaskId) {
        if (hardFilterTaskId == null || hardFilterTaskId.isBlank()) {
            return ApiResponse.error(400, "hardFilterTaskId 不能为空");
        }
        AnalyzeTaskStatusDTO status = deepseekFilterService.getHardFilterTaskStatus(hardFilterTaskId);
        if (status == null) {
            return ApiResponse.error(404, "硬过滤任务不存在: " + hardFilterTaskId);
        }
        return ApiResponse.success(status);
    }

    @GetMapping("/analyze/{analyzeTaskId}")
    public ApiResponse<AnalyzeTaskStatusDTO> getAnalyzeTaskStatus(@PathVariable("analyzeTaskId") String analyzeTaskId) {
        AnalyzeTaskStatusDTO status = deepseekAnalyzeService.getAnalyzeTaskStatus(analyzeTaskId);
        if (status == null) {
            return ApiResponse.error(404, "analyze任务不存在: " + analyzeTaskId);
        }
        return ApiResponse.success(status);
    }
}
