package com.app.dto;

import lombok.Data;

/**
 * extract 阶段单条简历输入项（来自 text 表）。
 */
@Data
public class ExtractResumeItemDTO {
    private String text;
    private String businessResumeId;
}

