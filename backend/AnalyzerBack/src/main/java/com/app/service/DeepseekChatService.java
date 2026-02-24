package com.app.service;

import com.app.client.DeepseekClient;
import com.app.dto.ResumeDTO;
import com.app.tool.FileParserUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class DeepseekChatService {
    
    private final DeepseekClient deepseekClient;
    
    // 用于在 Java 对象和 JSON 数据之间进行转换
    private final ObjectMapper objectMapper;
    
    // 使用构造函数注入
    @Autowired
    public DeepseekChatService(DeepseekClient deepseekClient, ObjectMapper objectMapper) {
        this.deepseekClient = deepseekClient;
        this.objectMapper = objectMapper;
    }

    // 处理文件并转换简历内容格式
    public List<ResumeDTO> generateResumeDetailFromFile(
            String apiKey,
            MultipartFile file,
            boolean useSiliconFlow) throws IOException {

        // 1. 解析文件内容
        String content = FileParserUtil.extractTextFromFile(file);

        // 2. 构建Prompt
        String prompt = FileParserUtil.buildResumePrompt(content);

        // 3. 调用API
        String jsonResponse = deepseekClient.getResponse(apiKey, prompt, useSiliconFlow);

        // 4. 解析API响应
        return parseResumeResponse(jsonResponse);
    }

    /**
     * 简单对话：发送问题，返回大模型回复文本
     * 使用 DeepseekClient.getResponse(apiKey, prompt) 两参方法
     */
    public String chat(String apiKey, String message) throws IOException {
        String jsonResponse = deepseekClient.getResponse(apiKey, message);
        JsonNode root = objectMapper.readTree(jsonResponse);
        return root.path("choices").get(0).path("message").path("content").asText();
    }

    // 解析API返回的数据
    private List<ResumeDTO> parseResumeResponse(String jsonResponse) throws IOException {
        // 解析整个API响应
        JsonNode rootNode = objectMapper.readTree(jsonResponse);

        // 提取content字段（包含题目JSON数组）
        String content = rootNode.path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();

        JsonNode questionsNode = objectMapper.readTree(content).path("questions");

        // 解析
        return objectMapper.readValue(questionsNode.toString(), new TypeReference<List<ResumeDTO>>() {});
    }
}
