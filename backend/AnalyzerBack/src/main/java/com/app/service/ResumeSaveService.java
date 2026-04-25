package com.app.service;

import com.app.config.ZipResumeProperties;
import com.app.dto.TextDTO;
import com.app.tool.FileParserUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parse uploaded zip resumes into raw text records.
 */
@Service
public class ResumeSaveService {
    private static final List<Charset> ZIP_ENTRY_CHARSETS = Arrays.asList(
            StandardCharsets.UTF_8,
            Charset.forName("GBK"),
            Charset.forName("GB18030")
    );
    private static final List<Charset> TEXT_CONTENT_CHARSETS = Arrays.asList(
            StandardCharsets.UTF_8,
            Charset.forName("GB18030"),
            Charset.forName("GBK")
    );

    private final ZipResumeProperties properties;

    public ResumeSaveService(ZipResumeProperties properties) {
        this.properties = properties;
    }

    public List<TextDTO> parseZipToTexts(MultipartFile zipFile) throws IOException {
        String name = zipFile.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Please upload a .zip archive");
        }

        Exception lastDecodeException = null;
        for (Charset charset : ZIP_ENTRY_CHARSETS) {
            try {
                return parseZipToTexts(zipFile, charset);
            } catch (IOException e) {
                if (!isLikelyCharsetIssue(e)) {
                    throw e;
                }
                lastDecodeException = e;
            } catch (IllegalArgumentException e) {
                if (!isLikelyCharsetIssue(e)) {
                    throw e;
                }
                lastDecodeException = e;
            }
        }

        if (lastDecodeException != null) {
            throw new IllegalArgumentException(
                    "Unable to decode zip entry names. Repack the archive with UTF-8 or GBK filenames.",
                    lastDecodeException
            );
        }

        return parseZipToTexts(zipFile, StandardCharsets.UTF_8);
    }

    private List<TextDTO> parseZipToTexts(MultipartFile zipFile, Charset charset) throws IOException {
        List<TextDTO> result = new ArrayList<>();
        try (InputStream in = zipFile.getInputStream();
             ZipInputStream zis = new ZipInputStream(in, charset)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                try {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (result.size() >= properties.getMaxFiles()) {
                        throw new IllegalArgumentException(
                                "Too many files in zip archive, max supported: " + properties.getMaxFiles()
                        );
                    }

                    String entryName = entry.getName();
                    if (entryName == null || entryName.contains("..")) {
                        continue;
                    }
                    if (!isSupportedFile(entryName)) {
                        continue;
                    }

                    byte[] fileBytes = readEntryBytes(zis, properties.getMaxSingleFileBytes());
                    String text = extractTextFromZipEntry(entryName, fileBytes);

                    TextDTO dto = new TextDTO();
                    dto.setResumeId(UUID.randomUUID().toString());
                    dto.setFileName(entryName);
                    dto.setText(text);
                    result.add(dto);
                } finally {
                    zis.closeEntry();
                }
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

    private byte[] readEntryBytes(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[properties.getBufferSize()];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new IllegalArgumentException(
                        "A file inside the zip archive exceeds the limit: " + maxBytes + " bytes"
                );
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private boolean isLikelyCharsetIssue(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("malformed input")
                        || lower.contains("input length")
                        || lower.contains("unmappable character")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String extractTextFromZipEntry(String entryName, byte[] fileBytes) throws IOException {
        String lower = entryName.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".html")) {
            return decodeTextContent(fileBytes);
        }
        return FileParserUtil.extractTextFromFileName(entryName, new ByteArrayInputStream(fileBytes));
    }

    private String decodeTextContent(byte[] fileBytes) {
        if (fileBytes.length == 0) {
            return "";
        }
        if (hasUtf8Bom(fileBytes)) {
            return new String(fileBytes, 3, fileBytes.length - 3, StandardCharsets.UTF_8);
        }
        for (Charset charset : TEXT_CONTENT_CHARSETS) {
            try {
                return decodeStrict(fileBytes, charset);
            } catch (CharacterCodingException ignored) {
                // try next charset
            }
        }
        return new String(fileBytes, StandardCharsets.UTF_8);
    }

    private String decodeStrict(byte[] fileBytes, Charset charset) throws CharacterCodingException {
        CharsetDecoder decoder = charset.newDecoder();
        CharBuffer charBuffer = decoder.decode(ByteBuffer.wrap(fileBytes));
        return charBuffer.toString();
    }

    private boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF;
    }
}
