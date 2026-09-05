# 流程审批

## 功能说明

基于 Flowable BPMN 2.0 引擎的自定义工作流审批系统，支持多种业务流程的发起、审批、跟踪。

## 支持的流程类型

| 流程类型 | 说明 | 业务对象 |
|----------|------|---------|
| ONBOARD | 入职申请 | 简历（候选人） |
| LEAVE | 离职申请 | 员工 |
| TRANSFER | 调岗申请 | 员工 |
| REGULAR | 转正申请 | 员工 |

## 流程状态

| 状态码 | 说明 |
|--------|------|
| 0 | 待审批 |
| 1 | 已通过 |
| 2 | 已拒绝 |
| 3 | 已取消 |

## BPMN 流程设计

每个流程类型对应一个独立的 BPMN 文件，存放在 `src/main/resources/processes/` 目录：

- `onboarding.bpmn20.xml` - 入职流程
- `leave.bpmn20.xml` - 离职流程
- `transfer.bpmn20.xml` - 调岗流程
- `regular.bpmn20.xml` - 转正流程

### 流程节点命名规范

| 节点 | 命名规则 |
|------|---------|
| 审批通过结束 | `{flowId}_end` |
| 审批拒绝结束 | `{flowId}_end_rejected` |

## AI 助手操作

- **发起**："发起入职流程，候选人张三，部门技术部"
- **待办**："查看我的待办审批"
- **审批**："同意李四的离职申请"
- **轨迹**："查看入职流程的进度"

## 相关接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/flow/start` | POST | 发起流程 |
| `/flow/todo` | GET | 待办列表 |
| `/flow/approve` | POST | 审批通过 |
| `/flow/reject` | POST | 审批拒绝 |
| `/flow/cancel` | POST | 取消流程 |
| `/flow/trajectory` | GET | 流程轨迹 |
| `/flow/list` | GET | 流程列表 |