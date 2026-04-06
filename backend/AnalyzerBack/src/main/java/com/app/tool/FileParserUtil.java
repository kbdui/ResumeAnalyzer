package com.app.tool;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class FileParserUtil {

    public static final String RESUME_PROMPT = """
            请分析以下简历内容，提取关键信息并以JSON格式返回：
            
            请提取以下信息（如果没有相关信息，请设置为null）：
            1. 个人信息：姓名、联系方式、邮箱等
            2. 教育背景：学校、专业、学历、毕业时间等
            3. 工作经验：公司、职位、工作时间、职责描述等
            4. 技能：专业技能、编程语言、工具等
            5. 项目经验：项目名称、描述、技术栈等
            6. 证书/奖项：相关认证和获奖情况
            
            请以以下JSON格式返回：
            {
                "personal_info": {
                    "name": "姓名",
                    "contact": "联系方式",
                    "email": "邮箱"
                },
                "education": [
                    {
                        "school": "学校名称",
                        "major": "专业",
                        "degree": "学历",
                        "graduation_year": "毕业年份"
                    }
                ],
                "work_experience": [
                    {
                        "company": "公司名称",
                        "position": "职位",
                        "duration": "工作时间",
                        "description": "工作描述"
                    }
                ],
                "skills": ["技能1", "技能2", "技能3"],
                "projects": [
                    {
                        "name": "项目名称",
                        "description": "项目描述",
                        "technologies": ["技术1", "技术2"]
                    }
                ],
                "certificates": ["证书1", "证书2"]
            }
            """;

    /**
     * JD 硬过滤：三态判断说明与输出 schema（占位符依次为：JD 全文、候选人简历 JSON 字符串）
     */
    private static final String JD_HARD_FILTER_PROMPT_TEMPLATE = """
            你是招聘筛选助手。根据「岗位描述 JD」与「候选人简历数据」，对以下四个维度分别做三态判断：
            - PASS：依据简历可明确判断满足 JD 中该维度的硬性要求；
            - FAIL：依据简历可明确判断不满足、或与 JD 硬性要求矛盾；
            - UNKNOWN：简历信息不足、模糊或无法从给定内容中推断，不能明确判定 PASS 或 FAIL。

            四个维度在输出 JSON 中必须使用且仅使用以下四个键（英文，不可改名、不可增删键）：
            1) education —— 教育背景
            2) work_experience —— 工作经验
            3) skills —— 技能
            4) projects —— 项目经验

            每个维度的值必须是一个 JSON 对象，且必须包含以下字段（不可省略键名）：
            - dimension：字符串，对应维度的中文名称（如「教育背景」「工作经验」「技能」「项目经验」）；
            - status：字符串，仅允许三选一且必须大写：PASS、FAIL、UNKNOWN；
            - confidence：0 到 1 之间的小数，表示你对该判断的置信度；
            - reason：字符串，简要说明判断理由（中文）；
            - evidence：字符串数组，列出简历或 JD 中支撑判断的原文要点或短句（若无则可为空数组）。

            只输出一个 JSON 对象，不要输出任何其它说明文字、不要 Markdown 代码块。

            【岗位描述 JD】
            %s

            【候选人简历 JSON】
            %s
            """;

    /**
     * 构建 JD 硬过滤完整 prompt（JD 与简历 JSON 由调用方传入）
     */
    public static String buildJdHardFilterPrompt(String jdText, String resumeJson) {
        return JD_HARD_FILTER_PROMPT_TEMPLATE.formatted(jdText, resumeJson);
    }

    /**
     * 从 JD 中抽取用于 hybrid 匹配加权的关键词（输出值必须为空格分隔字符串）。
     */
    private static final String JD_HYBRID_KEYWORD_PROMPT_TEMPLATE = """
            你是招聘 JD 结构化助手。请从以下 JD 文本中抽取三个维度关键词：
            1) work_experience_keywords（工作经验相关）
            2) skills_keywords（技能相关）
            3) education_keywords（教育背景相关）

            输出要求：
            - 仅输出一个 JSON 对象，不要输出任何解释，不要 Markdown 代码块；
            - 必须且仅包含以下三个键：
              {
                "work_experience_keywords": "关键词1 关键词2 关键词3",
                "skills_keywords": "关键词1 关键词2 关键词3",
                "education_keywords": "关键词1 关键词2 关键词3"
              }
            - 每个字段值必须是“空格分隔”的关键词字符串；
            - 关键词尽量短、可检索，避免长句；
            - 若某维度信息不足，返回空字符串。

            【JD 文本】
            %s
            """;

    public static String buildJdHybridKeywordPrompt(String jdText) {
        return JD_HYBRID_KEYWORD_PROMPT_TEMPLATE.formatted(jdText);
    }

    /**
     * 基于 JD + Hybrid 打分信息生成最终简历评估。
     */
    private static final String RESUME_ANALYSIS_PROMPT_TEMPLATE = """
            你是资深招聘顾问。请根据岗位 JD 与候选人的 hybrid 筛选评分信息，给出该候选人的综合评估。

            输出要求：
            - 只输出一个 JSON 对象，不要输出说明文字，不要 Markdown 代码块；
            - 字段必须包含：
              {
                "overall_level": "STRONG_MATCH|POTENTIAL_MATCH|WEAK_MATCH",
                "overall_score": 0.0,
                "summary": "中文总结",
                "strengths": ["..."],
                "risks": ["..."],
                "suggestions": ["..."]
              }
            - overall_score 范围 0~1；
            - 结合给定评分字段（如 final_score / work_experience_score / skills_score / education_score / full_text_score）给出解释；
            - strengths/risks/suggestions 至少各 2 条。

            【岗位 JD】
            %s

            【候选人筛选信息（JSON）】
            %s
            """;

    public static String buildResumeAnalysisPrompt(String jdText, String hybridItemJson) {
        return RESUME_ANALYSIS_PROMPT_TEMPLATE.formatted(jdText, hybridItemJson);
    }

    public static String extractTextFromFile(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        try (InputStream inputStream = file.getInputStream()) {
            return extractTextFromFileName(fileName, inputStream);
        }
    }

    /**
     * 按文件名后缀解析文本（用于 zip 内文件等场景）
     */
    public static String extractTextFromFileName(String fileName, InputStream inputStream) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        int dot = fileName.lastIndexOf(".");
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new IllegalArgumentException("无法识别文件后缀: " + fileName);
        }
        String extension = fileName.substring(dot + 1).toLowerCase();
        switch (extension) {
            case "pdf":
                return parsePdf(inputStream);
            case "ppt":
                return parseLegacyPpt(inputStream);
            case "pptx":
                return parsePptx(inputStream);
            case "doc":
                return parseDoc(inputStream);
            case "docx":
                return parseDocx(inputStream);
            case "txt":
            case "md":
            case "html":
                return parseText(inputStream);
            default:
                throw new IllegalArgumentException("不支持的文件格式: " + extension);
        }
    }

    // 处理word文件格式
    private static String parseDoc(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        try (HWPFDocument document = new HWPFDocument(inputStream)) {
            WordExtractor extractor = new WordExtractor(document);
            String text = extractor.getText();
            content.append(text);
        }
        return content.toString();
    }

    private static String parseDocx(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                content.append(paragraph.getText()).append("\n");
            }
        }
        return content.toString();
    }

    // 文本文件解析
    private static String parseText(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private static String parsePdf(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                content.append(stripper.getText(document)).append("\n");
            }
        }
        return content.toString();
    }

    private static String parseLegacyPpt(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        try (HSLFSlideShow slideShow = new HSLFSlideShow(inputStream)) {
            for (var slide : slideShow.getSlides()) {
                for (var shape : slide.getShapes()) {
                    if (shape instanceof HSLFTextShape) {
                        content.append(((HSLFTextShape) shape).getText()).append("\n");
                    }
                }
            }
        }
        return content.toString();
    }

    private static String parsePptx(InputStream inputStream) throws IOException {
        StringBuilder content = new StringBuilder();
        try (XMLSlideShow slideShow = new XMLSlideShow(inputStream)) {
            for (var slide : slideShow.getSlides()) {
                for (var shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape) {
                        content.append(((XSLFTextShape) shape).getText()).append("\n");
                    }
                }
            }
        }
        return content.toString();
    }

    // 构建Prompt
    public static String buildResumePrompt(String content) {
        return RESUME_PROMPT + "\n\n文档内容：\n" + content;
    }
}
