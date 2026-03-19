package com.app.web;

import com.app.dto.ResumeDTO;
import com.app.dto.AnalyzeSubmitResponseDTO;
import com.app.dto.AnalyzeTaskStatusDTO;
import com.app.request.ChatRequest;
import com.app.service.DeepseekChatService;
import com.app.service.ResumeService;
import com.app.service.TaskDeepseekAnalyzeService;
import com.app.tool.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;

@RestController
@RequestMapping("/deepseek")
@CrossOrigin
public class ChatController {
    @Value("${deepseek.api.key}")
    private String apiKey;

    private final DeepseekChatService deepseekChatService;
    private final ResumeService resumeService;
    private final TaskDeepseekAnalyzeService taskDeepseekAnalyzeService;

    @Autowired
    public ChatController(DeepseekChatService deepseekChatService,
                          ResumeService resumeService,
                          TaskDeepseekAnalyzeService taskDeepseekAnalyzeService){
        this.deepseekChatService = deepseekChatService;
        this.resumeService = resumeService;
        this.taskDeepseekAnalyzeService = taskDeepseekAnalyzeService;
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
            String reply = deepseekChatService.chat(apiKey, request.getMessage());
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
            ResumeDTO result = deepseekChatService.generateResumeDetailFromFile(apiKey, file, Objects.equals(model, "v3"));
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
    @PostMapping("/{taskId}/analyze")
    public ApiResponse<AnalyzeSubmitResponseDTO> submitAnalyzeTask(
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "model", defaultValue = "v3") String model) {
        if (taskId == null || taskId.isBlank()) {
            return ApiResponse.error(400, "taskId 不能为空");
        }
        try {
            String analyzeTaskId = taskDeepseekAnalyzeService.submitAnalyzeTask(
                    taskId,
                    apiKey,
                    Objects.equals(model, "v3")
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
     * 查询批量分析任务是否完成
     */
    @GetMapping("/analyze/{analyzeTaskId}")
    public ApiResponse<AnalyzeTaskStatusDTO> getAnalyzeTaskStatus(@PathVariable("analyzeTaskId") String analyzeTaskId) {
        AnalyzeTaskStatusDTO status = taskDeepseekAnalyzeService.getAnalyzeTaskStatus(analyzeTaskId);
        if (status == null) {
            return ApiResponse.error(404, "analyzeTask 不存在: " + analyzeTaskId);
        }
        return ApiResponse.success(status);
    }
}
