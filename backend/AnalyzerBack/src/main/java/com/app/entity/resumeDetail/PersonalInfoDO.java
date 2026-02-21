package com.app.entity.resumeDetail;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人信息实体类
 */
@Data
@TableName("personal_info")
public class PersonalInfoDO {
    
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
     * 姓名
     */
    @TableField("name")
    private String name;
    
    /**
     * 联系方式
     */
    @TableField("contact")
    private String contact;
    
    /**
     * 邮箱
     */
    @TableField("email")
    private String email;
    
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

