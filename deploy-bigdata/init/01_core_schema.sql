-- ============================================================
-- HR Agent 大数据学习环境：核心业务表（与项目 init_schema.sql 对齐）
-- 仅建 CDC 链路所需的核心表 + 演示种子数据
-- ============================================================

CREATE TABLE `t_ability_tag` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `tag_name`    VARCHAR(64)  NOT NULL,
    `tag_category` VARCHAR(32) DEFAULT NULL COMMENT '标签分类',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_name` (`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='能力标签表';

CREATE TABLE `t_job_post` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `job_code`        VARCHAR(64)   NOT NULL,
    `job_name`        VARCHAR(128)  NOT NULL,
    `dept_name`       VARCHAR(64)   DEFAULT NULL,
    `work_city`       VARCHAR(64)   DEFAULT NULL,
    `salary_min`      DECIMAL(12,2) DEFAULT NULL,
    `salary_max`      DECIMAL(12,2) DEFAULT NULL,
    `education_req`   TINYINT       DEFAULT NULL,
    `work_year_req`   INT           DEFAULT NULL,
    `head_count`      INT           DEFAULT 1,
    `job_status`      TINYINT       DEFAULT 1,
    `is_public`       TINYINT       DEFAULT 1,
    `publish_time`    DATETIME      DEFAULT NULL,
    `close_time`      DATETIME      DEFAULT NULL,
    `creator_emp_id`  BIGINT        DEFAULT NULL,
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`     DATETIME      DEFAULT NULL,
    `is_deleted`      TINYINT       DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_job_status` (`job_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='招聘岗位表';

CREATE TABLE `t_resume` (
    `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
    `resume_name`       VARCHAR(64)   NOT NULL,
    `gender`             TINYINT       DEFAULT NULL,
    `expect_position`    VARCHAR(128)  DEFAULT NULL,
    `expect_city`        VARCHAR(64)   DEFAULT NULL,
    `work_years`         INT           DEFAULT NULL,
    `education`          TINYINT       DEFAULT NULL,
    `school`             VARCHAR(128)  DEFAULT NULL,
    `major`              VARCHAR(128)  DEFAULT NULL,
    `resume_content`     LONGTEXT      DEFAULT NULL,
    `resume_status`     TINYINT       DEFAULT 0,
    `delivery_source`    TINYINT       DEFAULT NULL,
    `target_job_id`      BIGINT        DEFAULT NULL,
    `match_score`        DECIMAL(5,2)  DEFAULT NULL,
    `owner_emp_id`       BIGINT        DEFAULT NULL,
    `create_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `update_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`        DATETIME      DEFAULT NULL,
    `is_deleted`         TINYINT       DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_resume_status` (`resume_status`),
    KEY `idx_target_job` (`target_job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历表';

CREATE TABLE `t_resume_ability_rel` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `resume_id`   BIGINT   NOT NULL,
    `tag_id`      BIGINT   NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_resume_id` (`resume_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历-能力标签关联表';

CREATE TABLE `t_agent_tool_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `session_id`    VARCHAR(64)  NOT NULL,
    `message_id`    BIGINT       DEFAULT NULL,
    `intent_code`   VARCHAR(64)  DEFAULT NULL,
    `tool_name`     VARCHAR(128) NOT NULL,
    `input_params`  TEXT         DEFAULT NULL,
    `output_result` TEXT         DEFAULT NULL,
    `status`        VARCHAR(16)  DEFAULT 'success',
    `duration_ms`   BIGINT       DEFAULT NULL,
    `error_message` TEXT         DEFAULT NULL,
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `is_deleted`    TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_status` (`status`),
    KEY `idx_tool_name` (`tool_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具调用日志表';

-- ---------- 演示种子数据 ----------
INSERT INTO `t_ability_tag` (`id`, `tag_name`, `tag_category`) VALUES
(1, 'Java', 'language'),
(2, 'Spring Boot', 'framework'),
(3, 'MySQL', 'database'),
(4, 'Kafka', 'architecture'),
(5, 'AI Agent', 'ai');

INSERT INTO `t_job_post` (`id`, `job_code`, `job_name`, `dept_name`, `work_city`, `salary_min`, `salary_max`, `education_req`, `work_year_req`, `head_count`, `job_status`, `publish_time`) VALUES
(1001, 'JOB-2026-001', '高级Java工程师', '技术部', '上海', 25000.00, 40000.00, 3, 5, 2, 1, NOW()),
(1002, 'JOB-2026-002', '大数据架构师', '技术部', '北京', 35000.00, 60000.00, 3, 8, 1, 1, NOW()),
(1003, 'JOB-2026-003', '前端工程师', '技术部', '深圳', 20000.00, 30000.00, 2, 3, 3, 1, NOW());

INSERT INTO `t_resume` (`id`, `resume_name`, `gender`, `expect_position`, `expect_city`, `work_years`, `education`, `school`, `major`, `resume_content`, `resume_status`, `delivery_source`, `target_job_id`, `match_score`) VALUES
(5001, '张三', 1, '高级Java工程师', '上海', 6, 3, '复旦大学', '计算机科学与技术', '6年Java开发经验，熟悉Spring Boot、MySQL、Kafka', 1, 1, 1001, 88.50),
(5002, '李四', 1, '大数据架构师', '北京', 9, 3, '清华大学', '软件工程', '9年大数据经验，精通Hadoop生态、Flink、ClickHouse', 0, 2, 1002, 92.00),
(5003, '王五', 0, '前端工程师', '深圳', 3, 2, '武汉大学', '信息工程', '3年Vue3前端经验', 0, 1, 1003, 76.00);

INSERT INTO `t_resume_ability_rel` (`resume_id`, `tag_id`) VALUES
(5001, 1), (5001, 2), (5001, 3), (5001, 4),
(5002, 4), (5002, 5);

INSERT INTO `t_agent_tool_log` (`session_id`, `message_id`, `intent_code`, `tool_name`, `status`, `duration_ms`) VALUES
('demo-session-001', 1, 'JOB_QUERY', 'job_search', 'success', 120),
('demo-session-001', 1, 'RESUME_MATCH', 'resume_match', 'success', 850),
('demo-session-002', 2, 'PERF_QUERY', 'performance_query', 'error', 45);