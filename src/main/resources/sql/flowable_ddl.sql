-- ============================================================
-- Flowable 工作流引擎集成 DDL
-- 执行前请确保数据库已存在 t_employee、t_flow_instance 表
-- ============================================================

-- 1. t_employee 补充角色、直属上级、密码字段（审批人解析 + 登录鉴权依赖）
-- 已执行过的字段会报 Duplicate column，忽略即可；或逐条执行未执行过的语句
ALTER TABLE t_employee ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'EMPLOYEE' COMMENT '角色：EMPLOYEE/DEPT_LEADER/HR/HRBP/ADMIN' AFTER work_city;
ALTER TABLE t_employee ADD COLUMN IF NOT EXISTS leader_id BIGINT DEFAULT NULL COMMENT '直属上级ID（t_employee.id）' AFTER role;
ALTER TABLE t_employee ADD COLUMN IF NOT EXISTS password VARCHAR(100) DEFAULT NULL COMMENT '登录密码（BCrypt哈希）' AFTER emp_name;

-- 默认密码 123456 的 BCrypt 哈希由后端启动时 DataInitializer 自动设置，无需手动 UPDATE


-- 角色字段索引（审批人按角色查询）
ALTER TABLE t_employee ADD INDEX idx_role (role);
-- 直属上级索引
ALTER TABLE t_employee ADD INDEX idx_leader_id (leader_id);

-- 2. t_flow_instance 补充索引（流程查询高频字段）
ALTER TABLE t_flow_instance ADD INDEX idx_proc_inst_id (flowable_proc_inst_id);
ALTER TABLE t_flow_instance ADD INDEX idx_flow_type_status (flow_type, flow_status);
ALTER TABLE t_flow_instance ADD INDEX idx_apply_emp (apply_emp_id);

-- 3. Flowable 标准表由引擎启动时自动创建（database-schema-update=true）
--    表前缀 ACT_，包含 ACT_RE_/ACT_RU_/ACT_HI_/ACT_GE_ 四组
--    无需手写 DDL

-- 4. 可选：流程审批记录表（业务侧留痕，Flowable 历史表已能满足基本需求）
CREATE TABLE IF NOT EXISTS t_flow_approval (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    flow_instance_id BIGINT NOT NULL COMMENT '流程实例ID（t_flow_instance.id）',
    task_id VARCHAR(64) COMMENT 'Flowable 任务ID',
    node_name VARCHAR(64) NOT NULL COMMENT '节点名称',
    approver_emp_id BIGINT NOT NULL COMMENT '审批人（t_employee.id）',
    action TINYINT NOT NULL COMMENT '1通过 2拒绝 3转办',
    comment VARCHAR(500) COMMENT '审批意见',
    form_data JSON COMMENT '表单数据',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_flow_instance (flow_instance_id),
    INDEX idx_approver (approver_emp_id)
) COMMENT='流程审批记录';
