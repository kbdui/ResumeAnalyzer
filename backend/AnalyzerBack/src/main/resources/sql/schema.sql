-- ResumeAnalyzer 数据库表结构
-- 字符集: utf8mb4，存储引擎: InnoDB
-- 执行前请先创建数据库: CREATE DATABASE IF NOT EXISTS resume_analyzer DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE resume_analyzer;

-- ----------------------------
-- 1. 简历主表
-- ----------------------------
DROP TABLE IF EXISTS `resume`;
CREATE TABLE `resume` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `resume_name`       VARCHAR(255)    DEFAULT NULL COMMENT '简历名称/标题',
    `original_filename` VARCHAR(255)    DEFAULT NULL COMMENT '原始文件名',
    `file_path`         VARCHAR(500)    DEFAULT NULL COMMENT '文件路径',
    `skills`            TEXT            DEFAULT NULL COMMENT '技能列表(JSON)',
    `certificates`      TEXT            DEFAULT NULL COMMENT '证书列表(JSON)',
    `create_time`       DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历主表';

-- ----------------------------
-- 2. 个人信息表（一对一 resume）
-- ----------------------------
DROP TABLE IF EXISTS `personal_info`;
CREATE TABLE `personal_info` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `resume_id`   BIGINT       NOT NULL COMMENT '简历ID',
    `name`        VARCHAR(100) DEFAULT NULL COMMENT '姓名',
    `contact`     VARCHAR(100) DEFAULT NULL COMMENT '联系方式',
    `email`       VARCHAR(255) DEFAULT NULL COMMENT '邮箱',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_resume_id` (`resume_id`),
    CONSTRAINT `fk_personal_info_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人信息表';

-- ----------------------------
-- 3. 教育经历表（一对多 resume）
-- ----------------------------
DROP TABLE IF EXISTS `education`;
CREATE TABLE `education` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `resume_id`       BIGINT       NOT NULL COMMENT '简历ID',
    `school`          VARCHAR(255) DEFAULT NULL COMMENT '学校名称',
    `major`           VARCHAR(255) DEFAULT NULL COMMENT '专业',
    `degree`          VARCHAR(50)  DEFAULT NULL COMMENT '学历',
    `graduation_year` VARCHAR(20)   DEFAULT NULL COMMENT '毕业年份',
    `sort_order`      INT          DEFAULT 0 COMMENT '排序',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_education_resume_id` (`resume_id`),
    CONSTRAINT `fk_education_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教育经历表';

-- ----------------------------
-- 4. 工作经历表（一对多 resume）
-- ----------------------------
DROP TABLE IF EXISTS `work_experience`;
CREATE TABLE `work_experience` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `resume_id`   BIGINT   NOT NULL COMMENT '简历ID',
    `company`     VARCHAR(255) DEFAULT NULL COMMENT '公司名称',
    `position`    VARCHAR(255) DEFAULT NULL COMMENT '职位',
    `duration`    VARCHAR(100) DEFAULT NULL COMMENT '工作时间',
    `description` TEXT     DEFAULT NULL COMMENT '工作描述',
    `sort_order`  INT      DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_work_experience_resume_id` (`resume_id`),
    CONSTRAINT `fk_work_experience_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作经历表';

-- ----------------------------
-- 5. 项目经历表（一对多 resume）
-- ----------------------------
DROP TABLE IF EXISTS `project`;
CREATE TABLE `project` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `resume_id`     BIGINT       NOT NULL COMMENT '简历ID',
    `name`          VARCHAR(255) DEFAULT NULL COMMENT '项目名称',
    `description`   TEXT         DEFAULT NULL COMMENT '项目描述',
    `technologies`   TEXT         DEFAULT NULL COMMENT '技术栈(JSON)',
    `sort_order`    INT          DEFAULT 0 COMMENT '排序',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_project_resume_id` (`resume_id`),
    CONSTRAINT `fk_project_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目经历表';
