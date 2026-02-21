package com.app.entity;

import com.app.entity.resumeDetail.EducationDO;
import com.app.entity.resumeDetail.PersonalInfoDO;
import com.app.entity.resumeDetail.ProjectDO;
import com.app.entity.resumeDetail.WorkExperienceDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 简历实体类
 */
@Data
@TableName("resume")
public class ResumeDO {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 简历名称/标题
     */
    @TableField("resume_name")
    private String resumeName;
    
    /**
     * 原始文件名
     */
    @TableField("original_filename")
    private String originalFilename;
    
    /**
     * 文件路径
     */
    @TableField("file_path")
    private String filePath;
    
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
    
    // 以下字段用于存储关联数据，实际存储时可能需要关联查询或JSON存储
    /**
     * 个人信息（关联查询或JSON存储）
     */
    @TableField(exist = false)
    private PersonalInfoDO personalInfo;
    
    /**
     * 教育经历列表（关联查询）
     */
    @TableField(exist = false)
    private List<EducationDO> education;
    
    /**
     * 工作经历列表（关联查询）
     */
    @TableField(exist = false)
    private List<WorkExperienceDO> workExperience;
    
    /**
     * 技能列表（可存储为JSON或关联表）
     */
    @TableField("skills")
    private String skills; // JSON格式存储
    
    /**
     * 项目经历列表（关联查询）
     */
    @TableField(exist = false)
    private List<ProjectDO> projects;
    
    /**
     * 证书列表（可存储为JSON）
     */
    @TableField("certificates")
    private String certificates; // JSON格式存储
}

