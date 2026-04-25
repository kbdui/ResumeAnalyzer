package com.app.service;

import com.app.client.DeepseekClient;
import com.app.dto.ResumeDTO;
import com.app.tool.FileParserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * DeepSeek API related service helpers.
 */
@Service
public class DeepseekBaseService {

    private final DeepseekClient deepseekClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public DeepseekBaseService(DeepseekClient deepseekClient, ObjectMapper objectMapper) {
        this.deepseekClient = deepseekClient;
        this.objectMapper = objectMapper;
    }

    public ResumeDTO generateResumeDetailFromFile(
            String apiKey,
            MultipartFile file,
            boolean useSiliconFlow) throws IOException {
        String content = FileParserUtil.extractTextFromFile(file);
        String prompt = FileParserUtil.buildResumePrompt(content);
        String jsonResponse = deepseekClient.getResponse(apiKey, prompt, false, false);
        return parseResumeResponse(jsonResponse);
    }

    public ResumeDTO generateResumeDetailFromText(
            String apiKey,
            String content,
            boolean useSiliconFlow) throws IOException {
        String prompt = FileParserUtil.buildResumePrompt(content);
        String jsonResponse = deepseekClient.getResponse(apiKey, prompt, false, false);
        return parseResumeResponse(jsonResponse);
    }

    public String chat(String apiKey, String message) throws IOException {
        String jsonResponse = deepseekClient.getResponse(apiKey, message);
        JsonNode root = objectMapper.readTree(jsonResponse);
        return root.path("choices").get(0).path("message").path("content").asText();
    }

    public JsonNode jdHardFilterAnalyze(
            String apiKey,
            String jdText,
            JsonNode resumePayload,
            boolean useSiliconFlow) throws IOException {
        String resumeJson;
        try {
            resumeJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resumePayload);
        } catch (Exception e) {
            resumeJson = String.valueOf(resumePayload);
        }
        String prompt = FileParserUtil.buildJdHardFilterPrompt(jdText, resumeJson);
        String jsonResponse = deepseekClient.getResponse(apiKey, prompt, false, false);
        return parseAssistantJson(jsonResponse);
    }

    public JsonNode extractHybridJdKeywords(
            String apiKey,
            String jdText,
            boolean useSiliconFlow) throws IOException {
        String prompt = FileParserUtil.buildJdHybridKeywordPrompt(jdText);
        String jsonResponse = deepseekClient.getResponse(apiKey, prompt, false, false);
        return parseAssistantJson(jsonResponse);
    }

    public JsonNode analyzeHybridResume(
            String apiKey,
            String jdText,
            JsonNode hybridItem,
            boolean useSiliconFlow) throws IOException {
        String hybridItemJson;
        try {
            hybridItemJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(hybridItem);
        } catch (Exception e) {
            hybridItemJson = String.valueOf(hybridItem);
        }
        String prompt = FileParserUtil.buildResumeAnalysisPrompt(jdText, hybridItemJson);
        String jsonResponse = deepseekClient.getResponse(apiKey, prompt, false, true);
        return parseAssistantJson(jsonResponse);
    }

    private JsonNode parseAssistantJson(String jsonResponse) throws IOException {
        String content = extractAssistantContent(jsonResponse);
        String json = normalizeJsonPayload(content);
        return objectMapper.readTree(json);
    }

    private String extractAssistantContent(String jsonResponse) throws IOException {
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        JsonNode choices = rootNode.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IOException("API response missing choices");
        }
        return choices.get(0).path("message").path("content").asText();
    }

    private ResumeDTO parseResumeResponse(String jsonResponse) throws IOException {
        String content = extractAssistantContent(jsonResponse);
        String json = normalizeJsonPayload(content);
        return objectMapper.readValue(json, ResumeDTO.class);
    }

    private static String normalizeJsonPayload(String content) throws IOException {
        if (content == null) {
            return "{}";
        }

        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return "{}";
        }

        String withoutFence = stripMarkdownFence(trimmed).trim();
        String extracted = extractFirstJsonBlock(withoutFence);
        if (extracted != null) {
            return extracted.trim();
        }

        if (withoutFence.startsWith("{") || withoutFence.startsWith("[")) {
            return withoutFence;
        }

        throw new IOException("Model content is not valid JSON: " + safeSnippet(withoutFence));
    }

    private static String stripMarkdownFence(String content) {
        String s = content.trim();
        while (s.startsWith("`")) {
            if (!s.startsWith("```")) {
                s = s.substring(1).trim();
                continue;
            }
            int firstNl = s.indexOf('\n');
            if (firstNl < 0) {
                return s.replace("```", "").trim();
            }
            s = s.substring(firstNl + 1).trim();
            int end = s.lastIndexOf("```");
            if (end >= 0) {
                s = s.substring(0, end).trim();
            }
        }
        return s;
    }

    private static String extractFirstJsonBlock(String text) {
        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        int start;
        char openChar;
        char closeChar;

        if (objectStart < 0 && arrayStart < 0) {
            return null;
        }
        if (objectStart >= 0 && (arrayStart < 0 || objectStart < arrayStart)) {
            start = objectStart;
            openChar = '{';
            closeChar = '}';
        } else {
            start = arrayStart;
            openChar = '[';
            closeChar = ']';
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == openChar) {
                depth++;
                continue;
            }
            if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static String safeSnippet(String s) {
        String clean = s.replaceAll("\\s+", " ").trim();
        return clean.length() <= 200 ? clean : clean.substring(0, 200) + "...";
    }
}
