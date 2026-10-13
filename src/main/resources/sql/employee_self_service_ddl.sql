-- ============================================================
-- 员工自助模块 增量 DDL（对运行中的 hr_agent_db 执行）
-- 用途：新增请假/证明/到期提醒业务表 + t_employee 加合同到期列
-- 执行：mysql -h127.0.0.1 -P13306 -uroot -pAdmin@888888 hr_agent_db < 本文件
-- 注意：与 init_schema.sql 不同，本脚本不做 DROP，可安全重复执行
-- ============================================================
SET NAMES utf8mb4;

-- 1. t_employee 增加劳动合同到期日期列（已存在则跳过）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA='hr_agent_db' AND TABLE_NAME='t_employee' AND COLUMN_NAME='contract_end_date');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE `t_employee` ADD COLUMN `contract_end_date` DATE DEFAULT NULL COMMENT ''劳动合同到期日期'' AFTER `regular_date`',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 请假申请表
CREATE TABLE IF NOT EXISTS `t_leave_request` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,
    `emp_id`            BIGINT        NOT NULL                COMMENT '申请员工ID(t_employee.id)',
    `leave_type`        VARCHAR(32)   NOT NULL                COMMENT '请假类型: 年假/事假/病假/调休/其他',
    `start_date`        DATE          NOT NULL                COMMENT '开始日期',
    `end_date`          DATE          NOT NULL                COMMENT '结束日期',
    `leave_days`        DECIMAL(5,1)  NOT NULL                COMMENT '请假天数',
    `reason`            VARCHAR(500)  DEFAULT NULL            COMMENT '请假原因',
    `status`            TINYINT       DEFAULT 0               COMMENT '状态 0审批中 1通过 2拒绝 3撤销',
    `flow_instance_id`  BIGINT        DEFAULT NULL            COMMENT '关联流程实例(t_flow_instance.id)',
    `create_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`       DATETIME      DEFAULT NULL,
    `is_deleted`        TINYINT       DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_emp` (`emp_id`),
    KEY `idx_status` (`status`),
    KEY `idx_flow_instance` (`flow_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='请假申请表';

-- 3. 假期余额表
CREATE TABLE IF NOT EXISTS `t_leave_balance` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT,
    `emp_id`      BIGINT        NOT NULL                COMMENT '员工ID(t_employee.id)',
    `year`        INT           NOT NULL                COMMENT '年度',
    `total_days`  DECIMAL(5,1)  NOT NULL                COMMENT '年度总假期天数',
    `used_days`   DECIMAL(5,1)  DEFAULT 0               COMMENT '已使用天数',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time` DATETIME      DEFAULT NULL,
    `is_deleted`  TINYINT       DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_emp_year` (`emp_id`, `year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='假期余额表';

-- 4. 证明开具申请表
CREATE TABLE IF NOT EXISTS `t_certificate_request` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,
    `emp_id`            BIGINT        NOT NULL                COMMENT '申请员工ID(t_employee.id)',
    `cert_type`         VARCHAR(32)   NOT NULL                COMMENT '证明类型: 在职证明/收入证明',
    `purpose`           VARCHAR(255)  DEFAULT NULL            COMMENT '用途说明',
    `status`            TINYINT       DEFAULT 0               COMMENT '状态 0审批中 1通过 2拒绝 3撤销',
    `content`           TEXT          DEFAULT NULL            COMMENT '审批通过后生成的证明内容',
    `flow_instance_id`  BIGINT        DEFAULT NULL            COMMENT '关联流程实例(t_flow_instance.id)',
    `create_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`       DATETIME      DEFAULT NULL,
    `is_deleted`        TINYINT       DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_emp` (`emp_id`),
    KEY `idx_status` (`status`),
    KEY `idx_flow_instance` (`flow_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='证明开具申请表';

-- 5. 到期提醒记录表
CREATE TABLE IF NOT EXISTS `t_expire_reminder` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `emp_id`      BIGINT       NOT NULL                COMMENT '员工ID(t_employee.id)',
    `remind_type` VARCHAR(32)  NOT NULL                COMMENT '提醒类型: CONTRACT(合同到期)/REGULAR(转正到期)',
    `expire_date` DATE         NOT NULL                COMMENT '到期日期',
    `remind_date` DATE         NOT NULL                COMMENT '提醒日期',
    `sent`        TINYINT      DEFAULT 1               COMMENT '是否已发送提醒 0否 1是',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time` DATETIME     DEFAULT NULL,
    `is_deleted`  TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_emp_type_date` (`emp_id`, `remind_type`, `expire_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='到期提醒记录表';

SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES
WHERE TABLE_SCHEMA='hr_agent_db' AND TABLE_NAME IN ('t_leave_request','t_leave_balance','t_certificate_request','t_expire_reminder');
