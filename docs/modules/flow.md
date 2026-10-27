# 流程审批

## 功能说明

基于 Flowable BPMN 2.0 引擎的自定义工作流审批系统，支持多种业务流程的发起、审批、跟踪。

## 支持的流程类型

| 流程类型 | 说明 | 业务对象（`biz_type`） |
|----------|------|------------------------|
| ONBOARD | 入职申请 | 简历（RESUME） |
| REGULAR | 转正申请 | 员工（EMPLOYEE） |
| TRANSFER | 调岗申请 | 员工（EMPLOYEE） |
| LEAVE | 离职申请 | 员工（EMPLOYEE） |
| LEAVE_REQUEST | 请假申请 | 请假单（LEAVE_REQUEST） |
| CERTIFICATE | 证明开具 | 证明申请（CERTIFICATE） |
| MULTI_SIGN | 会签 / 或签 | 由发起参数指定 |
| 自定义 | 设计器部署的流程 | 发起时指定 `bizType` |

## 流程状态

| 状态码 | 说明 |
|--------|------|
| 1 | 审批中 |
| 2 | 已通过 |
| 3 | 已拒绝 |
| 4 | 已撤回 |

## BPMN 流程设计

每个流程类型对应一个独立的 BPMN 文件，存放在 `src/main/resources/processes/` 目录：

- `onboard.bpmn20.xml` - 入职流程
- `regular.bpmn20.xml` - 转正流程
- `transfer.bpmn20.xml` - 调岗流程
- `leave.bpmn20.xml` - 离职流程
- `leave-request.bpmn20.xml` - 请假流程
- `certificate.bpmn20.xml` - 证明开具流程
- `multi-sign-process.bpmn20.xml` - 会签 / 或签流程

### 流程节点命名规范

| 节点 | 命名规则 |
|------|---------|
| 审批通过结束 | `{flowId}_end` |
| 审批拒绝结束 | `{flowId}_end_rejected` |

## 飞书审批卡片（可选）

审批待办可自动推送到审批人的飞书单聊，审批人点击卡片按钮即可完成审批，无需登录 Web 端。

**链路**

1. Flowable 创建用户任务（`TASK_CREATED`，且已指定具体审批人）时发布进程内事件；
2. 业务事务提交后异步推送：按审批人手机号（优先）或邮箱解析 `open_id`，发送含「同意 / 拒绝」按钮的交互卡片；
3. 用户点击后，飞书通过长连接回调 `card.action.trigger`，后端转为既有 `TaskCompleteDto` 驱动 Flowable 审批，并返回更新后的卡片。

**内置校验**

| 校验 | 说明 |
|------|------|
| 操作人身份 | 回调 `operator.open_id` 必须与卡片绑定的审批人 `open_id` 一致，防止卡片被转发后他人代审批 |
| 幂等 | 任务已处理时直接返回「已处理」卡片；重复点击 / 飞书事件重试不会二次流转 |
| 归属兜底 | 完成任务时再次校验任务 assignee 与审批人一致 |

处理完成后原卡片会被替换为结果卡片（去掉按钮），无法再次点击。
审批人未匹配到飞书账号时跳过推送并记录 WARN，Web 待办不受影响；未配置凭据时整体降级。

**配置**

```yaml
hragent:
  feishu:
    enabled: true
    # 为空时自动复用 hragent.mcp.feishu.app-id / app-secret（两者是同一个自建应用）
    app-id: ${FEISHU_APP_ID:}
    app-secret: ${FEISHU_APP_SECRET:}
```

飞书开放平台侧需完成：

- 自建应用开通 `im:message:send`（发送单聊消息）与 `contact:user.id:readonly`（通过手机号或邮箱获取用户 ID）应用身份权限；
- 开发者后台订阅「卡片回传交互 `card.action.trigger`」，订阅方式选择「使用长连接接收回调」（无需公网地址）；
- 将 `FEISHU_APP_ID` / `FEISHU_APP_SECRET` 写入环境变量或本地 `application-local.yaml`（该文件已被 `.gitignore` 忽略，禁止把密钥提交到仓库）。

**注意事项**

- 飞书按长连接分发事件：**同时运行多个后端实例时，回调会随机落到不同实例**，联调/生产环境应只保留一个长连接实例，否则会出现「点击偶发无响应」；
- 员工手机号必须是其真实飞书账号绑定的手机号；占位号码解析不到 `open_id` 时会静默跳过（日志打印 `审批人未匹配到飞书账号`）；
- 同一审批人短时间内多次收到同一任务卡片属于异常（事件被重复投递），可依据任务幂等直接忽略。

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