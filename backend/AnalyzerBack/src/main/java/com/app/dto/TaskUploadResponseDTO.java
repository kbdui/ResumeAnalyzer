package com.app.dto;

import lombok.Data;

/**
 * zip 上传并入库后的响应
 */
@Data
public class TaskUploadResponseDTO {
    private String taskId;
    private Integer resumeCount;
    private Integer savedCount;
}
