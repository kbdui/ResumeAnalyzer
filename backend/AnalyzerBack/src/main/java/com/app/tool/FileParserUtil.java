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

import static java.awt.SystemColor.text;

public class FileParserUtil {
    public static final String RESUME_PROMPT = """
            请分析以下简历内容，提取关键信息并以JSON格式返回：
            
            简历内容：
            %s
            
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
            """.formatted(text);

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
