# API 接口

## 基础信息

- **Base URL**: `http://localhost:8080`
- **Content-Type**: `application/json`

## 统一返回格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 响应码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 500 | 服务器内部错误 |
| 1001 | 请求体格式错误 |
| 1006 | 重复提交 |
| 1007 | 请求频率超限 |
| 1008 | 分布式锁获取超时 |

## 接口列表

### AI 助手

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/agent/chat` | AI 对话 |
| POST | `/agent/chat/continue` | 继续对话 |
| GET | `/agent/sessions` | 会话列表 |
| GET | `/agent/session/messages` | 会话消息 |
| DELETE | `/agent/session/clear` | 清空会话 |

### 员工管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/employee/list` | 员工列表 |
| GET | `/employee/get/{id}` | 员工详情 |
| POST | `/employee/save` | 新增/编辑 |
| DELETE | `/employee/delete` | 删除 |

### 简历管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/resume/list` | 简历列表 |
| GET | `/resume/get/{id}` | 简历详情 |
| POST | `/resume/save` | 新增简历 |
| PUT | `/resume/update` | 更新简历 |
| DELETE | `/resume/delete` | 删除简历 |

### 流程审批

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/flow/start` | 发起流程 |
| GET | `/flow/todo` | 待办列表 |
| POST | `/flow/approve` | 审批通过 |
| POST | `/flow/reject` | 审批拒绝 |
| POST | `/flow/cancel` | 取消流程 |
| GET | `/flow/trajectory` | 流程轨迹 |
| GET | `/flow/list` | 流程列表 |

### 绩效管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/performance/list` | 绩效列表 |
| GET | `/performance/get/{id}` | 绩效详情 |
| POST | `/performance/save` | 新增/编辑 |
| DELETE | `/performance/delete` | 删除 |

### 培训管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/training/course/list` | 课程列表 |
| POST | `/training/course/save` | 新增课程 |
| DELETE | `/training/course/delete` | 删除课程 |
| GET | `/training/tag/list` | 能力标签列表 |

### 数据看板

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/dashboard/system/summary` | 系统汇总 |
| GET | `/dashboard/system/ai-daily` | AI 每日趋势 |
| GET | `/dashboard/system/business-daily` | 业务每日趋势 |
| GET | `/dashboard/system/recent-activity` | 最近活动日志 |