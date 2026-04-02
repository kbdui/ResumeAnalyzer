package com.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务与业务简历（resume_id 字符串）关联：用于 LLM 三态过滤等。
 */
@Data
@TableName("task_resume")
public class TaskResumeDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    /**
     * 业务简历 ID，与 {@code text.resume_id}、{@code resume.resume_id} 一致。
     */
    @TableField("resume_id")
    private String resumeId;

    /**
     * 是否通过过滤（0 否 1 是）
     */
    @TableField("pass")
    private Boolean pass;

    @TableField("analysis_json")
    private String analysisJson;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
