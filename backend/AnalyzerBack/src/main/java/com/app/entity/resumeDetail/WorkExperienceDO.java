package com.app.entity.resumeDetail;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作经历实体类
 */
@Data
@TableName("work_experience")
public class WorkExperienceDO {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 简历ID（外键）
     */
    @TableField("resume_id")
    private Long resumeId;
    
    /**
     * 公司名称
     */
    @TableField("company")
    private String company;
    
    /**
     * 职位
     */
    @TableField("position")
    private String position;
    
    /**
     * 工作时间
     */
    @TableField("duration")
    private String duration;
    
    /**
     * 工作描述
     */
    @TableField("description")
    private String description;
    
    /**
     * 排序字段（用于控制显示顺序）
     */
    @TableField("sort_order")
    private Integer sortOrder;
    
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

