# 员工管理

## 功能说明

员工管理模块覆盖员工全生命周期管理，包括档案维护、入职、离职、调岗、转正等流程操作。

## 数据模型

| 字段 | 类型 | 说明 |
|------|------|------|
| emp_name | varchar(50) | 员工姓名 |
| emp_no | varchar(50) | 工号（唯一） |
| dept_name | varchar(50) | 部门名称 |
| position | varchar(50) | 职位 |
| mobile_phone | varchar(255) | 手机号（加密存储） |
| id_card | varchar(255) | 身份证号（加密存储） |
| base_salary | varchar(255) | 基本工资（加密存储） |
| employment_status | tinyint | 在职状态：0-在职，1-离职 |
| hire_date | date | 入职日期 |
| leave_date | date | 离职日期 |

## AI 助手操作

通过 AI 助手可以进行以下操作：

- **查询**："查询张三的员工信息"
- **列表**："列出技术部所有员工"
- **入职**："发起入职流程，新员工叫李四"
- **离职**："为王五发起离职流程"
- **调岗**："将赵六从技术部调到市场部"

## 相关接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/employee/list` | GET | 员工列表查询 |
| `/employee/get/{id}` | GET | 员工详情 |
| `/employee/save` | POST | 新增/编辑员工 |
| `/employee/delete` | DELETE | 删除员工 |