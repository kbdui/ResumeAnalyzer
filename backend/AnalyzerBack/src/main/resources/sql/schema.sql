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

-- ----------------------------
-- 6. 任务表（一次 zip 上传视为一个 task）
-- ----------------------------
DROP TABLE IF EXISTS `task`;
CREATE TABLE `task` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`       VARCHAR(64)   NOT NULL COMMENT '业务任务ID（如UUID）',
    `python_task_id` VARCHAR(128) DEFAULT NULL COMMENT 'Python异步任务ID',
    `resume_count`  INT           NOT NULL DEFAULT 0 COMMENT '简历数量',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '新建时间',
    `submitted`     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已提交分析 0否 1是',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_task_id` (`task_id`),
    KEY `idx_task_python_task_id` (`python_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='上传任务表';

-- ----------------------------
-- 7. 原始简历文本表（存储解析前的原始文本）
-- ----------------------------
DROP TABLE IF EXISTS `resume_text`;
CREATE TABLE `resume_text` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `resume_id`   VARCHAR(64)   NOT NULL COMMENT '业务简历ID（如UUID）',
    `file_name`   VARCHAR(255)  DEFAULT NULL COMMENT '原始文件名',
    `text`        MEDIUMTEXT    NOT NULL COMMENT '原始简历文本内容',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_resume_text_resume_id` (`resume_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原始简历文本表';

-- ----------------------------
-- 8. 任务-简历关联表（仅保留 task 与 resume_text 的关联）
-- ----------------------------
DROP TABLE IF EXISTS `task_resume`;
CREATE TABLE `task_resume` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`        BIGINT      NOT NULL COMMENT '任务ID',
    `resume_text_id` BIGINT      NOT NULL COMMENT '简历文本ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_resume_pair` (`task_id`, `resume_text_id`),
    KEY `idx_task_resume_task_id` (`task_id`),
    KEY `idx_task_resume_resume_text_id` (`resume_text_id`),
    CONSTRAINT `fk_task_resume_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_task_resume_resume_text` FOREIGN KEY (`resume_text_id`) REFERENCES `resume_text` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务与简历文本关联表';

-- ----------------------------
-- 9. 任务分析结果表（存储 task 经 FastAPI 处理后的分析结果）
-- ----------------------------
DROP TABLE IF EXISTS `task_result`;
CREATE TABLE `task_result` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`     BIGINT        NOT NULL COMMENT '任务ID',
    `status`      VARCHAR(32)   NOT NULL DEFAULT 'RUNNING' COMMENT '任务状态 RUNNING/SUCCESS/FAILED',
    `result_json` MEDIUMTEXT    NOT NULL COMMENT 'FastAPI返回的完整结果JSON',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_result_task_id` (`task_id`),
    CONSTRAINT `fk_task_result_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务分析结果表';

-- ----------------------------
-- 10. 任务与简历主表关联（保留筛选排序分数）
-- ----------------------------
DROP TABLE IF EXISTS `task_resume_main`;
CREATE TABLE `task_resume_main` (
    `id`         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`    BIGINT         NOT NULL COMMENT '任务ID',
    `resume_id`  BIGINT         NOT NULL COMMENT '简历主表ID',
    `rank_no`    INT            DEFAULT NULL COMMENT '在筛选结果中的名次（从1开始）',
    `final_score` DECIMAL(10,6) DEFAULT NULL COMMENT 'Python筛选结果 final_score',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_resume_main_task_id` (`task_id`),
    KEY `idx_task_resume_main_resume_id` (`resume_id`),
    CONSTRAINT `fk_task_resume_main_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_task_resume_main_resume` FOREIGN KEY (`resume_id`) REFERENCES `resume` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务与简历主表关联表';
