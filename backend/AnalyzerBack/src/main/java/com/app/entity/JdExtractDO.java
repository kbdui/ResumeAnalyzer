package com.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * JD 关键词提取结果（供 hybrid 匹配使用）。
 */
@Data
@TableName("jd_extract")
public class JdExtractDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    /**
     * 完整 JD 文本
     */
    @TableField("jd_text")
    private String jdText;

    /**
     * 工作经验关键词（空格分隔）
     */
    @TableField("work_experience_keywords")
    private String workExperienceKeywords;

    /**
     * 技能关键词（空格分隔）
     */
    @TableField("skills_keywords")
    private String skillsKeywords;

    /**
     * 教育关键词（空格分隔）
     */
    @TableField("education_keywords")
    private String educationKeywords;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}

