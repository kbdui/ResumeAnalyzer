package com.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 最终 LLM 简历评估结果。
 */
@Data
@TableName("analysis")
public class AnalysisDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    /**
     * 业务简历ID（与 text.resume_id / resume.resume_id 对应）
     */
    @TableField("resume_id")
    private String resumeId;

    /**
     * LLM 评估结果 JSON
     */
    @TableField("analysis_json")
    private String analysisJson;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

