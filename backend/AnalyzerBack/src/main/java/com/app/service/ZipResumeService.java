package com.app.service;

import com.app.dto.ResumeTextDTO;
import com.app.tool.FileParserUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 处理 zip 简历上传：将 zip 内多份文件解析为文本数组
 */
@Service
public class ZipResumeService {

    private static final int MAX_FILES = 1500;
    private static final int MAX_SINGLE_FILE_BYTES = 20 * 1024 * 1024; // 20MB
    private static final int BUFFER_SIZE = 8192;

    public List<ResumeTextDTO> parseZipToTexts(MultipartFile zipFile) throws IOException {
        String name = zipFile.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("请上传 zip 压缩包");
        }

        List<ResumeTextDTO> result = new ArrayList<>();
        try (InputStream in = zipFile.getInputStream();
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (result.size() >= MAX_FILES) {
                    throw new IllegalArgumentException("压缩包文件数量超限，最多支持 " + MAX_FILES + " 份");
                }
                String entryName = entry.getName();
                if (entryName == null || entryName.contains("..")) {
                    continue;
                }
                if (!isSupportedFile(entryName)) {
                    continue;
                }

                byte[] fileBytes = readEntryBytes(zis, MAX_SINGLE_FILE_BYTES);
                String text = FileParserUtil.extractTextFromFileName(entryName, new ByteArrayInputStream(fileBytes));

                ResumeTextDTO dto = new ResumeTextDTO();
                dto.setResumeId(UUID.randomUUID().toString());
                dto.setFileName(entryName);
                dto.setText(text);
                result.add(dto);
            }
        }
        return result;
    }

    private static boolean isSupportedFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".pdf")
                || lower.endsWith(".doc")
                || lower.endsWith(".docx")
                || lower.endsWith(".ppt")
                || lower.endsWith(".pptx")
                || lower.endsWith(".txt")
                || lower.endsWith(".md")
                || lower.endsWith(".html");
    }

    private static byte[] readEntryBytes(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[BUFFER_SIZE];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new IllegalArgumentException("压缩包内单文件过大，超过 " + maxBytes + " bytes");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}

