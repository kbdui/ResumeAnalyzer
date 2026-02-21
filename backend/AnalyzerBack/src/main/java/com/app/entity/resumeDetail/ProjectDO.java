package com.app.entity.resumeDetail;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 项目经历实体类
 */
@Data
@TableName("project")
public class ProjectDO {
    
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
     * 项目名称
     */
    @TableField("name")
    private String name;
    
    /**
     * 项目描述
     */
    @TableField("description")
    private String description;
    
    /**
     * 技术栈（JSON格式存储）
     */
    @TableField("technologies")
    private String technologies; // JSON格式存储技术栈列表
    
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

