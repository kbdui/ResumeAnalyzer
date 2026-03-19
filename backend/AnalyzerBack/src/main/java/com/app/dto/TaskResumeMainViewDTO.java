package com.app.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务下的简历分析结果展示对象
 */
@Data
public class TaskResumeMainViewDTO {
    private Long relationId;
    private Long resumeId;
    private Integer rankNo;
    private BigDecimal finalScore;
    private LocalDateTime createTime;
    private ResumeDTO resume;
}
