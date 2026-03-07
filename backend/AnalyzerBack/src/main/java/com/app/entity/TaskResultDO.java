package com.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务分析结果实体（存储 task 经 FastAPI 处理后的分析结果）
 */
@Data
@TableName("task_result")
public class TaskResultDO {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 任务状态 RUNNING/SUCCESS/FAILED
     */
    @TableField("status")
    private String status;

    /**
     * FastAPI 返回的完整结果 JSON
     */
    @TableField("result_json")
    private String resultJson;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
