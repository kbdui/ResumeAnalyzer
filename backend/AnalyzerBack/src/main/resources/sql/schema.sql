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
    `resume_id`         VARCHAR(64)     DEFAULT NULL COMMENT '业务简历ID（与 text.resume_id 对应，用于跨表关联）',
    `resume_name`       VARCHAR(255)    DEFAULT NULL COMMENT '简历名称/标题',
    `original_filename` VARCHAR(255)    DEFAULT NULL COMMENT '原始文件名',
    `file_path`         VARCHAR(500)    DEFAULT NULL COMMENT '文件路径',
    `skills`            TEXT            DEFAULT NULL COMMENT '技能列表(JSON)',
    `certificates`      TEXT            DEFAULT NULL COMMENT '证书列表(JSON)',
    `create_time`       DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_resume_business_resume_id` (`resume_id`)
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
DROP TABLE IF EXISTS `text`;
CREATE TABLE `text` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`     BIGINT        NOT NULL COMMENT '任务ID',
    `resume_id`   VARCHAR(64)   NOT NULL COMMENT '业务简历ID（如UUID）',
    `file_name`   VARCHAR(255)  DEFAULT NULL COMMENT '原始文件名',
    `text`        MEDIUMTEXT    NOT NULL COMMENT '原始简历文本内容',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_text_task_id` (`task_id`),
    KEY `idx_text_resume_id` (`resume_id`),
    CONSTRAINT `fk_text_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='原始简历文本表';

-- ----------------------------
-- 8. 任务与简历（业务 resume_id）关联：LLM 三态过滤等
-- ----------------------------
DROP TABLE IF EXISTS `task_resume`;
CREATE TABLE `task_resume` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`        BIGINT        NOT NULL COMMENT '任务ID',
    `resume_id`      VARCHAR(64)   NOT NULL COMMENT '业务简历ID（与 text.resume_id、resume.resume_id 对应）',
    `pass`           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否通过过滤（0否 1是）',
    `analysis_json`  MEDIUMTEXT    DEFAULT NULL COMMENT '分析结果 JSON',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_resume_task_resume` (`task_id`, `resume_id`),
    KEY `idx_task_resume_task_id` (`task_id`),
    CONSTRAINT `fk_task_resume_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务与业务简历关联（过滤/分析）';

-- ----------------------------
-- 9. JD 关键词提取结果表（供 hybrid 匹配加权）
-- ----------------------------
DROP TABLE IF EXISTS `jd_extract`;
CREATE TABLE `jd_extract` (
    `id`                         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`                    BIGINT        NOT NULL COMMENT '任务ID',
    `jd_text`                    MEDIUMTEXT    NOT NULL COMMENT '完整 JD 文本',
    `work_experience_keywords`   TEXT          DEFAULT NULL COMMENT '工作经验关键词（空格分隔）',
    `skills_keywords`            TEXT          DEFAULT NULL COMMENT '技能关键词（空格分隔）',
    `education_keywords`         TEXT          DEFAULT NULL COMMENT '教育关键词（空格分隔）',
    `create_time`                DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`                DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_jd_extract_task_id` (`task_id`),
    CONSTRAINT `fk_jd_extract_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='JD 关键词提取结果表';

-- ----------------------------
-- 10. 混合匹配结果表（Python FastAPI 词法/语义匹配结果，原 task_result）
-- ----------------------------
DROP TABLE IF EXISTS `hybrid_result`;
CREATE TABLE `hybrid_result` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`     BIGINT        NOT NULL COMMENT '任务ID',
    `status`      VARCHAR(32)   NOT NULL DEFAULT 'RUNNING' COMMENT '任务状态 RUNNING/SUCCESS/FAILED',
    `result_json` MEDIUMTEXT    NOT NULL COMMENT 'FastAPI返回的完整结果JSON',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hybrid_result_task_id` (`task_id`),
    CONSTRAINT `fk_hybrid_result_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='混合匹配结果表';

-- ----------------------------
-- 11. 最终评估表（LLM 基于 JD + hybrid 评分给出简历评估）
-- ----------------------------
DROP TABLE IF EXISTS `analysis`;
CREATE TABLE `analysis` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `task_id`      BIGINT        NOT NULL COMMENT '任务ID',
    `resume_id`    VARCHAR(64)   NOT NULL COMMENT '业务简历ID',
    `analysis_json` MEDIUMTEXT   NOT NULL COMMENT 'LLM评估结果JSON',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_analysis_task_resume` (`task_id`, `resume_id`),
    KEY `idx_analysis_task_id` (`task_id`),
    CONSTRAINT `fk_analysis_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='最终评估表';

