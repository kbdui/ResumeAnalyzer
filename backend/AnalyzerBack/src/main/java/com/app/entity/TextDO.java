package com.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 原始简历文本实体
 */
@Data
@TableName("text")
public class TextDO {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联任务ID（task.id）
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 业务侧的简历ID（可为 UUID 或关联 resume 表ID）
     */
    @TableField("resume_id")
    private String resumeId;

    /**
     * 原始文件名
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 原始简历文本内容
     */
    @TableField("text")
    private String text;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
