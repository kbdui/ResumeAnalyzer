package com.app.web;

import com.app.dto.ResumeDTO;
import com.app.dto.ResumeTextDTO;
import com.app.request.ChatRequest;
import com.app.request.PythonMatchTaskRequest;
import com.app.service.DeepseekChatService;
import com.app.service.PythonService;
import com.app.service.ResumeService;
import com.app.service.ZipResumeService;
import com.app.tool.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
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
public class ChatController {
    @Value("${deepseek.api.key}")
    private String apiKey;

    private final DeepseekChatService deepseekChatService;
    private final ResumeService resumeService;

    @Autowired
    public ChatController(DeepseekChatService deepseekChatService,
                          ResumeService resumeService){
        this.deepseekChatService = deepseekChatService;
        this.resumeService = resumeService;
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
}
