package com.app.entity.resumeDetail;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教育经历实体类
 */
@Data
@TableName("education")
public class EducationDO {
    
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
     * 学校名称
     */
    @TableField("school")
    private String school;
    
    /**
     * 专业
     */
    @TableField("major")
    private String major;
    
    /**
     * 学历
     */
    @TableField("degree")
    private String degree;
    
    /**
     * 毕业年份
     */
    @TableField("graduation_year")
    private String graduationYear;
    
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

