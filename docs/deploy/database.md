# 数据库设计

## 环境说明

| 组件 | 容器名称 | 宿主机端口 | 说明 |
|------|---------|-----------|------|
| MySQL 5.7 | douban-mysql | 13306 | 业务数据库 |
| Redis 6.2 | douban-redis | 16379 | 缓存 |
| 数据库名 | hr_agent_db | - | 字符集 utf8mb4 |

## ER 模型

```
t_employee ──┬── t_performance (emp_id)
             ├── t_flow_instance (apply_emp_id)
             ├── t_job_post (creator_emp_id)
             └── t_resume (owner_emp_id)

t_resume ────┬── t_job_post (target_job_id)
             └── t_ability_tag (通过 t_resume_ability_rel 中间表)

t_training_course ──── t_ability_tag (tag_ids 逗号分隔)
```

## 业务表清单

| 表名 | 说明 | 核心字段 |
|------|------|---------|
| t_employee | 员工表 | emp_name, emp_no, dept_name, position |
| t_resume | 简历表 | candidate_name, phone, resume_status |
| t_job_post | 岗位表 | post_name, dept_name, status |
| t_flow_instance | 流程实例表 | flow_type, flow_no, flow_status |
| t_performance | 绩效表 | performance_year, score |
| t_training_course | 培训课程表 | course_name, instructor, tag_ids |
| t_ability_tag | 能力标签表 | tag_name, category |
| t_resume_ability_rel | 简历-标签中间表 | resume_id, tag_id |
| t_agent_interact_log | AI 交互日志 | user_message, answer, input_tokens |

## 通用字段规范

所有业务表统一包含：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint unsigned | 主键自增 |
| create_time | datetime(3) | 创建时间 |
| update_time | datetime(3) | 更新时间 |
| delete_time | datetime(3) | 删除时间戳 |
| is_deleted | tinyint | 逻辑删除标记 0/1 |

> 敏感字段（手机号、身份证、基本工资）应用层 AES-256-CBC 加密存储，数据库只存密文。
> 不使用数据库外键约束，所有关联关系由应用层维护。