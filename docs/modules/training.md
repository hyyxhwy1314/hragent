# 培训管理

## 功能说明

培训管理模块支持培训课程的管理、能力标签匹配、员工培训记录跟踪。

## 数据模型

### 培训课程 (t_training_course)

| 字段 | 类型 | 说明 |
|------|------|------|
| course_name | varchar(100) | 课程名称 |
| description | text | 课程描述 |
| instructor | varchar(50) | 讲师 |
| duration_hours | int | 课时（小时） |
| max_participants | int | 最大参与人数 |
| tag_ids | varchar(255) | 关联能力标签 ID（逗号分隔） |

### 能力标签 (t_ability_tag)

| 字段 | 类型 | 说明 |
|------|------|------|
| tag_name | varchar(50) | 标签名称 |
| category | varchar(50) | 标签分类 |

## AI 助手操作

- **查询**："查询所有培训课程"
- **推荐**："推荐适合Java开发人员的培训课程"
- **新增**："新增一个Spring Cloud培训课程"

## 相关接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/training/course/list` | GET | 课程列表 |
| `/training/course/save` | POST | 新增课程 |
| `/training/course/delete` | DELETE | 删除课程 |
| `/training/tag/list` | GET | 能力标签列表 |