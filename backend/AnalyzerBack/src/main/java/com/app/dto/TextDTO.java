package com.app.dto;

import lombok.Data;

/**
 * 文本传输对象（用于批量上传与 Python 服务传输）
 */
@Data
public class TextDTO {
    private String resumeId;
    private String fileName;
    private String text;
}
