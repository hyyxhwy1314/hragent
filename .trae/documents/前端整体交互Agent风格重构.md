# 前端整体交互重构：主流 Agent 产品风格（深色侧栏 + 浅色内容）

## Context

用户要求参考主流 AI Agent 产品（Claude / ChatGPT / DeepSeek）的交互设计，全面重构 HR-Agent 前端整体交互功能。已与用户确认：

- **范围**：全面重构 —— 布局外壳 + AI 助手页 + 登录页 + 各业务页统一视觉
- **风格**：深色侧栏 + 浅色内容（类似 ChatGPT / Claude 桌面端）

现状关键发现：

- 后端 `/agent/chat` 已返回 `toolSteps`（工具调用步骤：toolName / arguments / result / timestamp，见 [agent.ts](file:///c:/Users/Administrator/Desktop/hragent/frontend/src/api/modules/agent.ts) 与 AgentScheduler.extractToolSteps），但聊天页当前完全未展示 —— 主流 Agent 交互的核心亮点缺失。
- 聊天页自带 240px 历史会话侧栏，与外壳侧栏形成"双栏"，不主流；DeepSeek 网页版的"会话抽屉 + 居中对话列"更符合 Agent 产品范式。
- 业务 CRUD 页（员工/简历/流程/待办等）已统一使用全局类（.page-container / .search-card / .table-card，定义于 style.css），视觉统一改动可控，主要通过全局令牌与主题统一，无需逐页重写。

## 设计决策

1. **外壳侧栏深色化**：深色 `#171a21` 系 + 分组菜单（工作台/管理），折叠态 232→80px 图标态带 Tooltip，激活项用 accent 浅底胶囊。保持上次已修复的"侧栏固定、内容区独立滚动"结构不动。
2. **AI 助手页改为居中对话**：去掉内部 240px 会话栏，改为深色会话抽屉（聊天页头部左上角按钮唤起）；聊天列 max-width ≈ 820px 居中；欢迎态展示能力卡片 + 快捷提问；助手消息下渲染工具调用步骤卡片；消息增加复制按钮；输入区带建议芯片与"Enter 发送"提示。
3. **登录页双栏**：左侧深色品牌区（呼应深色身份），右侧浅色表单区。
4. **业务页统一**：更新 style.css 全局类与 App.vue Ant Design 主题令牌，使全部表格/搜索卡片自动沿用新视觉；浏览器抽查，仅修明显不一致的 scoped 样式。

## 实施步骤

### 1. 设计令牌（style.css + App.vue）
- `frontend/src/style.css` `:root` 新增深色侧栏令牌：`--sidebar-bg: #171a21`、`--sidebar-bg-deep: #12141a`、`--sidebar-text: #c9ced6`、`--sidebar-text-muted: #7d838f`、`--sidebar-hover: rgba(255,255,255,0.06)`、`--sidebar-active: rgba(74,127,193,0.22)`、`--sidebar-border: #2a2f3a`；微调内容区圆角/阴影（--radius-md 等）。
- `frontend/src/App.vue`：内容区 Ant 主题令牌保持不变（浅色），侧栏菜单深色通过 Menu `theme="dark"` + scoped 样式覆盖实现（不动全局 theme token，避免影响浅色内容区组件）。

### 2. 外壳（BasicLayout.vue）
- Sider 深色背景 + 白字 logo；`Menu theme="dark"`，分组标题（工作台/管理）用小字号 muted 灰；选中项 accent 浅底胶囊；折叠图标态 hover 显示 Tooltip 菜单名。
- Header 白底保持，左侧新增「新建对话」按钮 → 跳转 `/agent`（新会话）。
- 保留上次修复的 `.app-root / .app-content / .menu-wrap` 滚动结构。

### 3. AI 助手页（views/agent/index.vue，核心）
- 删除内部 `.history-sidebar`（240px 会话栏），改为 `a-drawer` 深色会话抽屉（左侧滑出），聊天页头部加「历史会话」开关按钮；会话增删/切换逻辑复用现有函数。
- 聊天列居中：`.chat-messages` 改为内容 max-width 820px 居中，消息/思考/工具步骤/输入区均在此列内。
- 欢迎态：问候语 + 能力卡片网格（4~6 张，点击直接发送）+ 现有快捷提问 chips。
- 工具调用步骤：assistant 消息下方渲染 `toolSteps` 卡片列表（工具名 + 点击展开 arguments 详情），默认折叠。
- 消息操作：assistant 消息 hover 显示「复制」按钮（navigator.clipboard）；保留 Token 角标与思考折叠。
- 输入区：rounded 卡片 + focus 高亮（现有样式保留），空消息时输入框下方显示建议 chips；保留 "Enter 发送 / Shift+Enter 换行" 提示。
- 保留全部既有逻辑：流式打字、思考折叠默认收起、token 角标、`?q=` 直达、新对话/清屏。

### 4. 登录页（views/login/index.vue）
- 改为双栏：左侧深色品牌区（logo、HR-Agent、能力标语、极简图形），右侧浅色登录表单（工号/密码/登录按钮 + 默认密码提示）。

### 5. 业务页统一
- style.css 全局类细化：.page-header 标题层级、.search-card / .table-card 圆角与阴影微调（令牌驱动）。
- 抽查 employee / resume / flow / todo / selfService 页面，仅修正与全局明显不一致的 scoped 样式。
- dashboard 已重构完成，仅让顶部快捷提问输入与「新建对话」入口风格协调（小改）。

## 关键文件

- `frontend/src/style.css` —— 令牌 + 全局类
- `frontend/src/App.vue` —— 主题
- `frontend/src/layouts/BasicLayout.vue` —— 深色侧栏外壳
- `frontend/src/views/agent/index.vue` —— AI 助手页重构（核心）
- `frontend/src/views/login/index.vue` —— 登录页
- 抽查修正：`views/employee`、`views/resume`、`views/flow`、`views/todo`、`views/selfService` 等 scoped 样式

## 验证

1. `cd frontend && npx vue-tsc --noEmit -p tsconfig.json`
2. 浏览器 E2E（browser_use 子代理，登录 EMP002/123456）：
   - `/agent`：居中对话列、会话抽屉开关、提问后展示工具调用步骤卡片、助手消息复制按钮、思考折叠、输入建议芯片、`?q=` 直达
   - `/dashboard`：新风格协调、快捷提问可用
   - `/login`：双栏布局
   - `/employees` 等业务页：搜索卡片/表格新视觉
   - 侧栏折叠图标态、上下滚动侧栏固定、无 JS 报错

## 约束

- 保持中文注释、无 emoji、不破坏后端与既有功能。
- 深色侧栏文本对比度 ≥ 4.5:1（#c9ced6 文本配 #171a21 背景）。
- 若浏览器抽查发现某页 scoped 样式与全局冲突，优先改全局令牌，个别页最小修正。
