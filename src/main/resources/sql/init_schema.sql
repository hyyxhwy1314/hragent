-- ============================================================
-- HRAgent 业务表 DDL + 内置账号初始化
-- 数据库: hr_agent_db (MySQL 8.0, utf8mb4)
-- 容器: douban-mysql (127.0.0.1:13306, root/Admin@888888)
-- 设计要点:
--   1. 所有业务表继承 BaseEntity (id/create_time/update_time/delete_time/is_deleted)
--   2. 员工 password 字段留 NULL, 后端 DataInitializer 启动时自动填充 123456 的 BCrypt 哈希
--   3. 角色体系: EMPLOYEE/DEPT_LEADER/HR/HRBP/ADMIN
--   4. Flowable ACT_* 表由引擎启动时自动创建 (database-schema-update=true)
-- ============================================================

USE hr_agent_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 员工表 t_employee
-- ============================================================
DROP TABLE IF EXISTS `t_employee`;
CREATE TABLE `t_employee` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `emp_no`        VARCHAR(32)  NOT NULL                COMMENT '工号',
    `emp_name`      VARCHAR(64)  NOT NULL                COMMENT '员工姓名',
    `password`      VARCHAR(100) DEFAULT NULL            COMMENT '登录密码(BCrypt哈希, NULL则启动时自动填充123456)',
    `gender`        TINYINT      DEFAULT NULL             COMMENT '性别 0女 1男',
    `birth_date`    DATE         DEFAULT NULL             COMMENT '出生日期',
    `phone`         VARCHAR(32)  DEFAULT NULL             COMMENT '手机号',
    `email`         VARCHAR(128) DEFAULT NULL             COMMENT '邮箱',
    `id_card`       VARCHAR(32)  DEFAULT NULL             COMMENT '身份证号',
    `dept_name`     VARCHAR(64)  DEFAULT NULL             COMMENT '部门名称',
    `position_name` VARCHAR(64)  DEFAULT NULL             COMMENT '岗位名称',
    `entry_date`    DATE         DEFAULT NULL             COMMENT '入职日期',
    `regular_date`  DATE         DEFAULT NULL             COMMENT '转正日期',
    `leave_date`    DATE         DEFAULT NULL             COMMENT '离职日期',
    `emp_status`    TINYINT      DEFAULT 1                COMMENT '在职状态 0离职 1在职 2试用',
    `base_salary`   VARCHAR(32)  DEFAULT NULL             COMMENT '基本工资',
    `work_city`     VARCHAR(64)  DEFAULT NULL             COMMENT '工作城市',
    `role`          VARCHAR(20)  DEFAULT 'EMPLOYEE'       COMMENT '角色: EMPLOYEE/DEPT_LEADER/HR/HRBP/ADMIN',
    `leader_id`     BIGINT       DEFAULT NULL             COMMENT '直属上级ID(t_employee.id)',
    `remark`        VARCHAR(255) DEFAULT NULL             COMMENT '备注',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP                              COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP  COMMENT '更新时间',
    `delete_time`   DATETIME     DEFAULT NULL             COMMENT '删除时间',
    `is_deleted`    TINYINT      DEFAULT 0                COMMENT '逻辑删除 0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_emp_no` (`emp_no`),
    KEY `idx_role` (`role`),
    KEY `idx_leader_id` (`leader_id`),
    KEY `idx_dept` (`dept_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工表';

-- ============================================================
-- 2. 招聘岗位表 t_job_post
-- ============================================================
DROP TABLE IF EXISTS `t_job_post`;
CREATE TABLE `t_job_post` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `job_code`        VARCHAR(64)   NOT NULL                COMMENT '岗位编码',
    `job_name`        VARCHAR(128)  NOT NULL                COMMENT '岗位名称',
    `dept_name`       VARCHAR(64)   DEFAULT NULL            COMMENT '所属部门',
    `work_city`       VARCHAR(64)   DEFAULT NULL            COMMENT '工作城市',
    `work_address`    VARCHAR(255)  DEFAULT NULL            COMMENT '工作地址',
    `job_duty`        TEXT          DEFAULT NULL            COMMENT '岗位职责',
    `job_requirement` TEXT          DEFAULT NULL            COMMENT '任职要求',
    `salary_min`      DECIMAL(12,2) DEFAULT NULL            COMMENT '薪资下限',
    `salary_max`      DECIMAL(12,2) DEFAULT NULL            COMMENT '薪资上限',
    `education_req`   TINYINT       DEFAULT NULL            COMMENT '学历要求',
    `work_year_req`   INT           DEFAULT NULL            COMMENT '工作年限要求',
    `head_count`      INT           DEFAULT 1               COMMENT '招聘人数',
    `job_status`      TINYINT       DEFAULT 1               COMMENT '岗位状态 0关闭 1开放',
    `is_public`      TINYINT       DEFAULT 1               COMMENT '是否对外发布 0否 1是',
    `publish_time`    DATETIME      DEFAULT NULL            COMMENT '发布时间',
    `close_time`      DATETIME      DEFAULT NULL            COMMENT '截止时间',
    `creator_emp_id`  BIGINT        DEFAULT NULL            COMMENT '创建该岗位的HR(t_employee.id)',
    `jd_es_doc_id`    VARCHAR(128)  DEFAULT NULL            COMMENT 'ES文档id',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`     DATETIME      DEFAULT NULL,
    `is_deleted`      TINYINT       DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_job_status` (`job_status`),
    KEY `idx_creator` (`creator_emp_id`),
    KEY `idx_dept` (`dept_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='招聘岗位表';

-- ============================================================
-- 3. 简历表 t_resume
-- ============================================================
DROP TABLE IF EXISTS `t_resume`;
CREATE TABLE `t_resume` (
    `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
    `resume_name`       VARCHAR(64)   NOT NULL                COMMENT '候选人姓名',
    `gender`             TINYINT       DEFAULT NULL            COMMENT '性别 0女 1男',
    `birth_date`         DATE          DEFAULT NULL            COMMENT '出生日期',
    `phone`              VARCHAR(64)   DEFAULT NULL            COMMENT '手机号(密文)',
    `email`              VARCHAR(128)  DEFAULT NULL            COMMENT '邮箱(密文)',
    `id_card`            VARCHAR(64)   DEFAULT NULL            COMMENT '身份证(密文)',
    `expect_position`    VARCHAR(128)  DEFAULT NULL            COMMENT '意向岗位',
    `expect_salary_min`  DECIMAL(12,2) DEFAULT NULL            COMMENT '期望薪资下限',
    `expect_salary_max`  DECIMAL(12,2) DEFAULT NULL            COMMENT '期望薪资上限',
    `expect_city`        VARCHAR(64)   DEFAULT NULL            COMMENT '意向城市',
    `work_years`         INT           DEFAULT NULL            COMMENT '工作年限',
    `education`          TINYINT       DEFAULT NULL            COMMENT '学历',
    `school`             VARCHAR(128)  DEFAULT NULL            COMMENT '毕业学校',
    `major`              VARCHAR(128)  DEFAULT NULL            COMMENT '专业',
    `resume_content`     LONGTEXT      DEFAULT NULL            COMMENT '简历原始文本',
    `resume_struct_json` JSON          DEFAULT NULL            COMMENT 'AI解析后结构化json',
    `resume_file_id`     BIGINT        DEFAULT NULL            COMMENT '文件id(t_sys_file.id)',
    `resume_status`     TINYINT       DEFAULT 0               COMMENT '简历状态 0待筛选 1面试中 2录用 3归档',
    `delivery_source`    TINYINT       DEFAULT NULL            COMMENT '简历来源',
    `target_job_id`      BIGINT        DEFAULT NULL            COMMENT '投递岗位id(t_job_post.id)',
    `match_score`        DECIMAL(5,2)  DEFAULT NULL            COMMENT 'AI人岗匹配分数',
    `screening_opinion`  TEXT         DEFAULT NULL            COMMENT 'AI筛选评语',
    `owner_emp_id`       BIGINT        DEFAULT NULL            COMMENT '负责HR(t_employee.id)',
    `remark`             VARCHAR(255) DEFAULT NULL,
    `create_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `update_time`        DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`        DATETIME      DEFAULT NULL,
    `is_deleted`         TINYINT       DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_resume_status` (`resume_status`),
    KEY `idx_target_job` (`target_job_id`),
    KEY `idx_owner` (`owner_emp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历表';

-- ============================================================
-- 4. 流程实例表 t_flow_instance
-- ============================================================
DROP TABLE IF EXISTS `t_flow_instance`;
CREATE TABLE `t_flow_instance` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `flow_no`               VARCHAR(64)  NOT NULL                COMMENT '业务流水号',
    `flow_type`             VARCHAR(32)  NOT NULL                COMMENT '流程类型: PERFORMANCE/REGULAR/LEAVE/ONBOARD',
    `biz_id`                BIGINT       DEFAULT NULL            COMMENT '业务主键id',
    `apply_emp_id`          BIGINT       NOT NULL                COMMENT '申请人(t_employee.id)',
    `flow_status`           TINYINT      DEFAULT 0               COMMENT '业务流程状态 0进行中 1通过 2拒绝 3撤销',
    `flowable_proc_inst_id` VARCHAR(64)  DEFAULT NULL            COMMENT 'Flowable原生流程实例id',
    `biz_json`              JSON         DEFAULT NULL            COMMENT '业务扩展json',
    `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`           DATETIME     DEFAULT NULL,
    `is_deleted`            TINYINT     DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_flow_no` (`flow_no`),
    KEY `idx_proc_inst_id` (`flowable_proc_inst_id`),
    KEY `idx_flow_type_status` (`flow_type`, `flow_status`),
    KEY `idx_apply_emp` (`apply_emp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实例表';

-- ============================================================
-- 5. 流程审批记录表 t_flow_approval
-- ============================================================
DROP TABLE IF EXISTS `t_flow_approval`;
CREATE TABLE `t_flow_approval` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `flow_instance_id`  BIGINT       NOT NULL                COMMENT '流程实例ID(t_flow_instance.id)',
    `task_id`           VARCHAR(64)  DEFAULT NULL            COMMENT 'Flowable任务ID',
    `node_name`         VARCHAR(64)  NOT NULL                COMMENT '节点名称',
    `approver_emp_id`   BIGINT       NOT NULL                COMMENT '审批人(t_employee.id)',
    `action`            TINYINT      NOT NULL                COMMENT '1通过 2拒绝 3转办',
    `comment`           VARCHAR(500) DEFAULT NULL            COMMENT '审批意见',
    `form_data`         JSON         DEFAULT NULL            COMMENT '表单数据',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`       DATETIME     DEFAULT NULL,
    `is_deleted`        TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_flow_instance` (`flow_instance_id`),
    KEY `idx_approver` (`approver_emp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程审批记录表';

-- ============================================================
-- 6. 绩效表 t_performance
-- ============================================================
DROP TABLE IF EXISTS `t_performance`;
CREATE TABLE `t_performance` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,
    `emp_id`            BIGINT        NOT NULL                COMMENT '所属员工id(t_employee.id)',
    `period_code`       VARCHAR(32)   NOT NULL                COMMENT '绩效周期 如 2026Q3 / 2026-08',
    `kpi_json`          JSON          DEFAULT NULL            COMMENT 'KPI目标json',
    `self_score`        DECIMAL(5,2)  DEFAULT NULL            COMMENT '员工自评分数',
    `leader_score`      DECIMAL(5,2)  DEFAULT NULL            COMMENT '主管评分',
    `final_score`       DECIMAL(5,2)  DEFAULT NULL            COMMENT '最终得分',
    `performance_level` VARCHAR(4)    DEFAULT NULL            COMMENT '绩效等级 S/A/B/C/D',
    `ai_comment`        TEXT          DEFAULT NULL            COMMENT 'AI生成评语',
    `flow_instance_id`  BIGINT        DEFAULT NULL            COMMENT '关联流程实例id(t_flow_instance.id)',
    `status`            TINYINT       DEFAULT 0               COMMENT '状态 0草稿 1已提交 2审批完成',
    `create_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`       DATETIME      DEFAULT NULL,
    `is_deleted`        TINYINT       DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_emp_period` (`emp_id`, `period_code`),
    KEY `idx_flow_instance` (`flow_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绩效表';

-- ============================================================
-- 7. 培训课程表 t_training_course
-- ============================================================
DROP TABLE IF EXISTS `t_training_course`;
CREATE TABLE `t_training_course` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `course_name`   VARCHAR(128) NOT NULL                COMMENT '课程名称',
    `course_code`   VARCHAR(64)  DEFAULT NULL            COMMENT '课程编码',
    `course_type`   VARCHAR(32)  DEFAULT NULL            COMMENT '课程类型',
    `course_desc`   TEXT         DEFAULT NULL            COMMENT '课程描述',
    `course_target` TEXT         DEFAULT NULL            COMMENT '学习目标',
    `duration_min`  INT          DEFAULT NULL            COMMENT '课程时长(分钟)',
    `tag_ids`       VARCHAR(255) DEFAULT NULL            COMMENT '关联标签id,逗号分隔',
    `status`        TINYINT      DEFAULT 1               COMMENT '状态 0下架 1上架',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`   DATETIME     DEFAULT NULL,
    `is_deleted`    TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_course_code` (`course_code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='培训课程表';

-- ============================================================
-- 8. 能力标签表 t_ability_tag
-- ============================================================
DROP TABLE IF EXISTS `t_ability_tag`;
CREATE TABLE `t_ability_tag` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `tag_name`      VARCHAR(64)  NOT NULL                COMMENT '标签名称',
    `tag_code`      VARCHAR(64)  DEFAULT NULL            COMMENT '标签编码',
    `tag_category`  VARCHAR(32)  DEFAULT NULL            COMMENT '标签分类',
    `sort`          INT          DEFAULT 0               COMMENT '排序',
    `status`        TINYINT      DEFAULT 1               COMMENT '状态 0禁用 1启用',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`   DATETIME     DEFAULT NULL,
    `is_deleted`    TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tag_code` (`tag_code`),
    KEY `idx_category` (`tag_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='能力标签表';

-- ============================================================
-- 9. 简历-能力标签关联表 t_resume_ability_rel
-- ============================================================
DROP TABLE IF EXISTS `t_resume_ability_rel`;
CREATE TABLE `t_resume_ability_rel` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `resume_id`       BIGINT        NOT NULL                COMMENT '简历id(t_resume.id)',
    `ability_tag_id`  BIGINT        NOT NULL                COMMENT '能力标签id(t_ability_tag.id)',
    `confidence`      DECIMAL(5,2)  DEFAULT NULL            COMMENT 'AI置信度',
    `source`          VARCHAR(16)   DEFAULT NULL            COMMENT '来源: AI/HR',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`     DATETIME      DEFAULT NULL,
    `is_deleted`      TINYINT       DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_resume_id` (`resume_id`),
    KEY `idx_tag_id` (`ability_tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='简历-能力标签关联表';

-- ============================================================
-- 10. 系统文件表 t_sys_file
-- ============================================================
DROP TABLE IF EXISTS `t_sys_file`;
CREATE TABLE `t_sys_file` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `object_key`    VARCHAR(255) NOT NULL                COMMENT '对象存储key',
    `original_name` VARCHAR(255) DEFAULT NULL            COMMENT '原始文件名',
    `file_type`     VARCHAR(64)  DEFAULT NULL            COMMENT '文件类型(MIME或扩展名)',
    `file_size`     BIGINT       DEFAULT NULL            COMMENT '文件大小(字节)',
    `storage_type`  VARCHAR(32)  DEFAULT NULL            COMMENT '存储类型: COS',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`   DATETIME     DEFAULT NULL,
    `is_deleted`    TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_object_key` (`object_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统文件表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 内置账号数据
-- 密码策略: password 字段全部留 NULL
--   后端 DataInitializer 启动时会自动给 password=NULL 的员工
--   填充默认密码 123456 的 BCrypt 哈希, 无需在 SQL 中硬编码
-- 登录方式: emp_no + 密码 123456
-- ============================================================
INSERT INTO `t_employee` (`emp_no`, `emp_name`, `password`, `gender`, `phone`, `email`, `dept_name`, `position_name`, `entry_date`, `emp_status`, `work_city`, `role`, `leader_id`, `remark`) VALUES
-- 1. 超级管理员
('ADMIN001', '系统管理员', NULL, 1, '13800000001', 'admin@hragent.com', '信息技术部', '系统管理员', '2025-01-01', 1, '北京', 'ADMIN', NULL, '超级管理员,拥有全部权限'),

-- 2. HR (入职流程审批人, role=HR, 代码 OnboardAssigneeStrategy 会按此角色查询)
('HR001', '李HR', NULL, 0, '13800000002', 'hr.li@hragent.com', '人力资源部', '招聘主管', '2025-03-15', 1, '北京', 'HR', 1, '招聘负责人,负责简历筛选与入职流程发起'),

-- 3. 部门主管 (作为 leader_id 被员工引用, 用于审批)
('LEAD001', '张主管', NULL, 1, '13800000003', 'leader.zhang@hragent.com', '研发中心', '研发总监', '2024-06-01', 1, '北京', 'DEPT_LEADER', 1, '研发部门主管,负责部门员工审批'),

-- 4. HRBP (业务伙伴, 复杂场景审批)
('HRBP001', '王HRBP', NULL, 0, '13800000004', 'hrbp.wang@hragent.com', '人力资源部', 'HRBP', '2024-08-20', 1, '上海', 'HRBP', 2, '业务合作伙伴'),

-- 5. 普通员工1 (直属张主管)
('EMP001', '赵员工', NULL, 1, '13800000005', 'emp.zhao@hragent.com', '研发中心', 'Java工程师', '2025-07-01', 2, '北京', 'EMPLOYEE', 3, '试用期员工'),

-- 6. 普通员工2 (直属张主管, 已转正)
('EMP002', '钱员工', NULL, 1, '13800000006', 'emp.qian@hragent.com', '研发中心', '前端工程师', '2024-09-01', 1, '北京', 'EMPLOYEE', 3, '已转正员工'),

-- 7. 普通员工3 (直属张主管, 上海)
('EMP003', '孙员工', NULL, 0, '13800000007', 'emp.sun@hragent.com', '研发中心', '产品经理', '2025-02-10', 1, '上海', 'EMPLOYEE', 3, '上海办公'),

-- 8. 第二个部门主管 (销售部门)
('LEAD002', '周主管', NULL, 1, '13800000008', 'leader.zhou@hragent.com', '销售部', '销售总监', '2023-05-15', 1, '上海', 'DEPT_LEADER', 1, '销售部门主管'),

-- 9. 销售部普通员工 (直属周主管)
('EMP004', '吴销售', NULL, 1, '13800000009', 'emp.wu@hragent.com', '销售部', '客户经理', '2025-04-01', 1, '上海', 'EMPLOYEE', 8, '销售人员'),

-- 10. 第二个HR (负责上海招聘)
('HR002', '陈HR', NULL, 0, '13800000010', 'hr.chen@hragent.com', '人力资源部', '招聘专员', '2025-05-20', 1, '上海', 'HR', 2, '上海招聘负责人');

-- ============================================================
-- 内置能力标签 (供简历AI解析匹配使用)
-- ============================================================
INSERT INTO `t_ability_tag` (`tag_name`, `tag_code`, `tag_category`, `sort`, `status`) VALUES
('Java',        'java',         '编程语言',  1, 1),
('Spring Boot', 'spring-boot',  '框架',      2, 1),
('MySQL',       'mysql',        '数据库',    3, 1),
('Redis',       'redis',        '中间件',    4, 1),
('前端开发',     'frontend',     '技术方向',  5, 1),
('产品经理',     'pm',           '岗位',      6, 1),
('项目管理',     'pmp',          '通用能力',  7, 1),
('销售经验',     'sales',        '通用能力',  8, 1);

-- ============================================================
-- 验证
-- ============================================================
SELECT '=== 表清单 ===' AS info;
SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA='hr_agent_db' ORDER BY TABLE_NAME;

SELECT '=== 内置员工 ===' AS info;
SELECT id, emp_no, emp_name, dept_name, position_name, role, leader_id, emp_status FROM t_employee ORDER BY id;

SELECT '=== 能力标签 ===' AS info;
SELECT id, tag_name, tag_code, tag_category FROM t_ability_tag ORDER BY sort;
