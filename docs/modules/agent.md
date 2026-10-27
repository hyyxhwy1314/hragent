# AI 智能助手

## 功能说明

AI 智能助手是本系统的核心亮点，基于 LangChain4j + LangGraph4j 构建的状态机驱动的 Agent 系统，支持自然语言交互完成所有 HR 业务操作。

## 技术架构

```
用户输入
    │
    ▼
┌──────────────┐
│  ModelThink   │  ← 调用 LLM 分析意图，选择工具
│    节点       │
└──────┬───────┘
       │ 需要调用工具？
       ├─────是─────►┌──────────────┐     ┌──────────────┐
       │              │  ToolExecute  │────►│  ModelThink   │
       │              │    节点       │     │   (继续推理)  │
       │              └──────────────┘     └──────────────┘
       │
       └─────否─────► 输出最终回答
```

## 核心特性

### 动态工具加载

系统根据用户意图**动态加载**相关工具，减少 Token 消耗：

| 意图分类 | 加载工具数 | 对比（全部加载） |
|----------|-----------|----------------|
| 员工查询 | 2 个 | 节省 90% |
| 简历管理 | 4 个 | 节省 65% |
| 流程审批 | 7 个 | 节省 55% |
| 模糊查询 | 全部 20 个 | 兜底不遗漏 |

### 工具列表

| 分类 | 工具 | 说明 |
|------|------|------|
| 员工 | queryEmployee, listEmployee | 员工信息查询与列表 |
| 简历 | queryResume, listResume, saveResume, analyzeResume | 简历管理 |
| 流程 | startFlow, queryFlow, approveFlow, rejectFlow... | 流程审批 |
| 岗位 | queryJobPost, listJobPost | 岗位管理 |
| 绩效 | queryPerformance, savePerformance | 绩效管理 |
| 培训 | queryTrainingCourse, saveTrainingCourse | 培训管理 |
| 知识 | queryPolicy, hrKnowledgeQA | 制度查询、知识库检索问答（RAG，返回真实召回条款片段） |

### 思考过程展示

AI 助手会展示完整的思考过程，包括：
1. 推理过程（用户可折叠查看）
2. 工具调用步骤（调用了什么工具、参数是什么）
3. Token 消耗统计（每次对话的输入/输出 Token 数）

## 知识库检索（RAG）

系统内置向量检索的知识库问答能力，AI 工具 `hrKnowledgeQA` 由原先只返回模板话术升级为返回知识库真实召回的条款片段。

### 分层存储

| 数据 | 存储位置 |
|------|---------|
| 原文与元数据 | MySQL 表 `t_knowledge_chunk` |
| 向量 | Qdrant 集合 `hr_knowledge` |

向量库为 Docker 容器 `douban-qdrant`，映射端口 6333（REST）/ 6334（gRPC）；客户端**直接调用 Qdrant REST API**（JDK HttpClient + Jackson），**未引入**向量库 SDK 依赖。

### 向量化模型

复用阿里云 MaaS 兼容端点，模型 `qwen3.7-text-embedding-flash`（1024 维），与对话模型**共用** `ai.base-url` / `ai.api-key`，仅模型名不同，配置项为 `ai.embedding.model.name`（环境变量 `AI_EMBEDDING_MODEL_NAME`）。向量维度必须与 `hragent.rag.qdrant.vector-dim`（默认 1024）一致，**更换 embedding 模型后必须重建索引**，否则新旧向量空间不可比。

### 文档切分（结构感知）

切分器 `MarkdownChunker` 按业界主流的 Markdown 结构感知方式切分，而非固定长度硬切：

- **按标题层级切分**：片段归属章节，并携带「一级 &gt; 二级」章节路径，避免跨章节语义串味；
- **原子块**：表格、列表、代码块整体成块，绝不切断表格行或列表条目；超长表格按行拆分并重复表头；
- **父子块（small-to-big）**：章节过长时拆为多个子块入库检索，同时保存整节正文为父块，命中子块后自动回填父块补足上下文；
- 只有超长段落才按 `。！？；` 等句子边界二次切分，并按 `chunk-overlap` 重叠。

### 检索链路（两阶段）

```
查询向量化
  ──► Qdrant 扩大召回（recall-top-k，带 status/visibility/有效期硬过滤）
  ──► qwen3.7-text-rerank 精排（失败自动回退向量粗排）
  ──► 同章节去重 + 父块回填
  ──► 返回带来源引用的片段（现行条款 或 失效提示）
```

精排模型与主模型同一厂商、同一访问地址与密钥，配置项 `hragent.rag.rerank.*`（环境变量 `AI_RERANK_*`）。

### 过期治理

文档带 `status` / `effective-from` / `effective-to` / `review-due-at` / `version` 元数据，四级状态：

| 状态 | 含义 | 是否参与检索 |
|------|------|-------------|
| ACTIVE | 现行有效 | 是（且有效期校验通过） |
| SUPERSEDED | 已被新版本替代 | 否，仅可被“失效提示”召回 |
| EXPIRED | 已失效 | 否，仅可被“失效提示”召回 |
| DRAFT | 草稿未发布 | 否，不入向量库 |

- 检索层按状态与日期**硬过滤**，即使巡检任务异常，过期文档也不会被当作现行条款召回；
- 现行与失效片段**一次性统一精排**（同批次得分可比）：当失效片段更贴题时，只返回“该制度已失效/被替代”的提示，不返回弱相关的现行条款；
- `KnowledgeReviewJob` 每日巡检：逾期文档置 EXPIRED，复审截止前 N 天邮件提醒 HR/HRBP/ADMIN（按员工+复审日去重）。

### 内置知识文档与索引入库

内置知识文档位于 `src/main/resources/knowledge/*.md`，共 11 篇，覆盖入职、考勤、请假、加班、薪酬、绩效、培训、调岗、保密、行为奖惩、差旅、远程办公、实习生、离职等场景，其中包含故意构造的 EXPIRED / DRAFT 文档用于验证过期治理。应用启动时**幂等**切分并向量化入库（MySQL 已有片段则跳过）。

重建索引：将 `hragent.rag.reindex` 设为 `true` 并重启即可（会清空旧片段与 Qdrant 集合并重新向量化）。

### 配置项 `hragent.rag`

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `enabled` | true | 是否启用知识库检索 |
| `top-k` | 5 | 精排后返回片段数 |
| `recall-top-k` | 20 | 精排前召回候选数 |
| `min-score` | 0.3 | 向量召回相似度下限 |
| `chunk-size` | 400 | 子块目标字符数 |
| `chunk-overlap` | 80 | 超长段落切分重叠 |
| `parent-max-chars` | 1200 | 父块回填字符上限 |
| `rerank.enabled` | true | 是否启用精排（`AI_RERANK_ENABLED`） |
| `rerank.model-name` | qwen3.7-text-rerank | 精排模型（`AI_RERANK_MODEL_NAME`） |
| `rerank.path` | /api/v1/services/rerank/text-rerank/text-rerank | 精排接口路径（`AI_RERANK_PATH`） |
| `rerank.api-key` | 空 → 回退 `AI_API_KEY` | 精排密钥（`AI_RERANK_API_KEY`） |
| `freshness.hint-min-score` | 0.5 | 失效提示触发阈值 |
| `freshness.default-review-days` | 365 | 默认复审周期（天） |
| `freshness.grace-days` | 30 | 复审逾期宽限天数 |
| `freshness.reminder-days` | 30 | 复审提醒提前天数 |
| `reindex` | false | 是否重建索引 |
| `qdrant.{host,port,collection,vector-dim}` | — | Qdrant 连接与集合、向量维度 |

增量建表脚本 `src/main/resources/sql/rag_knowledge_ddl.sql` 需**手动执行**。核心代码位于 `agent/rag/`：`KnowledgeRetrievalService`、`KnowledgeBaseInitializer`、`MarkdownChunker`、`RerankClient`、`QdrantVectorClient`、`RagProperties`；时效巡检位于 `schedule/KnowledgeReviewJob`。

## 工具路由

工具选择采用分层 Tool Routing：注册表 → 确定性硬过滤 → 粗分类 → BM25 召回 Top-K → 模型精选 → 执行前鉴权。代码位于 `agent/routing/`（`ToolRouter` 顶部有中文链路注释）。

### 业务域决策（粗分类层）

粗分类由 `DomainDecisionStrategy` 承担，有两种实现，通过装配层切换（`RoutingDecisionConfig`）：

- `ToolClassifier`：基于关键词规则，默认启用，永远可用；
- `LocalModelDomainDecisionStrategy`：本地轻量模型实现，仅在 `ai.routing.enabled=true` 时装配，并以规则实现作为兜底。

本地模型只输出「业务域标签集合」这一短结构化结果（标签集合由 `ToolClassifier.supportedDomains()` 统一提供，提示词与输出校验共用），工具参数生成与最终作答仍由远程模型负责。调用失败、超时、输出非法或标签为空时自动回退规则分类，因此该决策点可一键回退（把 `ai.routing.enabled` 置为 `false`）。部署与配置见 [安装指南](../guide/installation.md)。

### 工具规划（模型精选层）

「选工具 + 生成参数」这一跳由 `ToolPlanningStrategy` 承担，有两种实现，同样通过装配层切换（`RoutingDecisionConfig`）：

- `NoopToolPlanningStrategy`：不做规划，交回远程模型按候选 Schema 自选（默认启用，行为与启用前一致）；
- `LocalModelToolPlanningStrategy`：本地轻量模型实现，仅在 `ai.routing.planning.enabled=true`（且 `ai.routing.enabled=true`）时装配。

本地规划只对**只读**候选工具生效（`ToolCard.readOnly()`），写操作与流程类工具仍由远程模型选定；
仅首轮触发——后续轮次需依据工具结果继续决策，本地规划器拿不到该上下文。命中时由 `ModelThinkNode` 直接下发
`TOOL_CALLS`/`ROUTED_TOOLS` 跳过远程首跳（每轮远程调用由 2 跳降为 1 跳）。规划调用失败、超时、输出非法、
仅含候选外工具或空规划时，均返回空并交回远程模型自选，因此可一键回退（把 `ai.routing.planning.enabled` 置为 `false`）。

### 全量本地化（主模型切换）

`ai.routing.local-primary=true` 时，按类型注入的主模型（`ChatLanguageModel`）切为本地模型，
使 Agent 最终作答、工具参数生成，以及简历深度分析、合同审查等能力全部改由本地模型完成。
切换同样在装配层以互斥条件完成（`LangChainConfig` 中两个 `@Primary` 选择器 Bean），无运行时条件分支。

远程 API 模型 Bean（`qwenChatModel`）与其凭据配置**始终保留**，仅不再作为注入首选，关闭开关即一键切回。
本地主模型独立配置超时（`ai.routing.llm-timeout-seconds`，默认 300 秒）——本地 CPU 推理较慢，路由决策用短超时
快速失败回退，而作答需给足时间。注意：主模型本地化后，「域分类 / 只读规划」两项优化的目的（省远程 token）已不适用，
建议保持 `ai.routing.enabled=false`（域分类走规则），避免每轮多出多余的本地调用与延迟。

## 对话示例

**用户**: "查询技术部所有员工"

**AI 思考过程**:
1. 分析用户意图：需要查询员工列表，按部门筛选
2. 调用工具：`listEmployee`，参数：`deptName=技术部`
3. 获取结果并格式化输出

**AI 回答**:
```
技术部共有 5 名员工：
1. 张三 - 技术经理
2. 李四 - 高级开发工程师
...
```

## 提示工程

系统使用精心设计的 System Prompt 来控制 AI 行为：

- 角色设定为专业的 HR 智能助手
- 每次回答必须调用相关工具获取实时数据
- 不能凭空编造数据
- 回答格式规范，使用 Markdown 增强可读性