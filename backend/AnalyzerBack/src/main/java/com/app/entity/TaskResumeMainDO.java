package com.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务与简历主表关联实体（保留筛选排序分数）
 */
@Data
@TableName("task_resume_main")
public class TaskResumeMainDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("resume_id")
    private Long resumeId;

    @TableField("rank_no")
    private Integer rankNo;

    @TableField("final_score")
    private BigDecimal finalScore;

    @TableField("create_time")
    private LocalDateTime createTime;
}
