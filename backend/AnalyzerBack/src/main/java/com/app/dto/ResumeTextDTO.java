package com.app.dto;

import lombok.Data;

/**
 * 简历文本传输对象（用于批量上传与 Python 服务传输）
 */
@Data
public class ResumeTextDTO {
    private String resumeId;
    private String fileName;
    private String text;
}

