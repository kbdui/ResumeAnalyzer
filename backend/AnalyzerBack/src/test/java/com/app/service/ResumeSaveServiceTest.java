package com.app.service;

import com.app.config.ZipResumeProperties;
import com.app.dto.TextDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeSaveServiceTest {

    @Test
    void parseZipToTexts_supportsGbkZipEntryNameAndTextContent() throws IOException {
        ResumeSaveService service = new ResumeSaveService(buildProperties());
        String fileName = "\u5f20\u4e09-\u7b80\u5386.txt";
        String content = "\u59d3\u540d\uff1a\u5f20\u4e09\n\u6280\u80fd\uff1aJava";
        byte[] zipBytes = buildZip(fileName, content, Charset.forName("GBK"), Charset.forName("GBK"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resumes.zip",
                "application/zip",
                zipBytes
        );

        List<TextDTO> texts = service.parseZipToTexts(file);

        assertEquals(1, texts.size());
        assertEquals(fileName, texts.get(0).getFileName());
        assertTrue(texts.get(0).getText().contains("\u59d3\u540d\uff1a\u5f20\u4e09"));
        assertTrue(texts.get(0).getText().contains("\u6280\u80fd\uff1aJava"));
    }

    private ZipResumeProperties buildProperties() {
        ZipResumeProperties properties = new ZipResumeProperties();
        properties.setMaxFiles(10);
        properties.setMaxSingleFileBytes(1024 * 1024);
        properties.setBufferSize(1024);
        return properties;
    }

    private byte[] buildZip(String fileName, String content, Charset zipCharset, Charset contentCharset)
            throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, zipCharset)) {
            zipOutputStream.putNextEntry(new ZipEntry(fileName));
            zipOutputStream.write(content.getBytes(contentCharset));
            zipOutputStream.closeEntry();
        }
        return outputStream.toByteArray();
    }
}
