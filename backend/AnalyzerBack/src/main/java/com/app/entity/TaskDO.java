package com.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传任务实体（一次 zip 上传视为一个 task）
 */
@Data
@TableName("task")
public class TaskDO {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务任务ID（如 UUID）
     */
    @TableField("task_id")
    private String taskId;

    /**
     * Python 异步任务ID
     */
    @TableField("python_task_id")
    private String pythonTaskId;

    /**
     * 简历数量
     */
    @TableField("resume_count")
    private Integer resumeCount;

    /**
     * 新建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 是否已提交分析 0否 1是
     */
    @TableField("submitted")
    private Integer submitted;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
