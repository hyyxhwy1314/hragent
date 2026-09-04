# 绩效管理

## 功能说明

绩效管理模块支持员工绩效目标的设定、考核评分、结果归档与查询。

## 数据模型

| 字段 | 类型 | 说明 |
|------|------|------|
| emp_id | bigint | 员工 ID |
| performance_year | int | 考核年份 |
| performance_quarter | int | 考核季度（1-4） |
| score | decimal(5,2) | 考核评分 |
| evaluator | varchar(50) | 评价人 |
| flow_instance_id | bigint | 关联审批流程实例 ID |

## AI 助手操作

- **查询**："查询张三-2026年第一季度的绩效"
- **录入**："为张三录入2026年Q1绩效，评分90"
- **列表**："查询技术部所有人的绩效"

## 相关接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/performance/list` | GET | 绩效列表 |
| `/performance/get/{id}` | GET | 绩效详情 |
| `/performance/save` | POST | 新增/编辑绩效 |
| `/performance/delete` | DELETE | 删除绩效 |