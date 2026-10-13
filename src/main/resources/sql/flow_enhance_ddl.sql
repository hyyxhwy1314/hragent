-- ============================================================
-- 流程能力增强增量 DDL（已部署库执行；全新库由 init_schema.sql 覆盖）
-- 包含：抄送表 t_flow_cc + 流程查询性能索引
-- ============================================================

-- 1. 流程抄送记录表 t_flow_cc
CREATE TABLE IF NOT EXISTS `t_flow_cc` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `flow_instance_id`  BIGINT       NOT NULL                COMMENT '流程实例ID(t_flow_instance.id)',
    `node_name`         VARCHAR(64)  DEFAULT NULL            COMMENT '抄送发生的节点名称',
    `cc_emp_id`         BIGINT       NOT NULL                COMMENT '抄送人(t_employee.id)',
    `read_flag`         TINYINT      DEFAULT 0               COMMENT '读取标记 0未读 1已读',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`       DATETIME     DEFAULT NULL,
    `is_deleted`        TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_cc_emp` (`cc_emp_id`),
    KEY `idx_cc_flow_instance` (`flow_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程抄送记录表';

-- 2. t_flow_instance 查询性能索引
--    幂等校验：同类型+同业务对象查进行中实例
--    列表分页：按状态 + 创建时间排序
ALTER TABLE `t_flow_instance` ADD INDEX `idx_type_biz` (`flow_type`, `biz_id`);
ALTER TABLE `t_flow_instance` ADD INDEX `idx_status_create` (`flow_status`, `create_time`);

-- 3. 抄送按人+已读筛选索引
ALTER TABLE `t_flow_cc` ADD INDEX `idx_cc_emp_read` (`cc_emp_id`, `read_flag`);

-- 4. 异常巡检游标分页索引：按状态筛选、按 id 递增遍历
ALTER TABLE `t_flow_instance` ADD INDEX `idx_status_id` (`flow_status`, `id`);