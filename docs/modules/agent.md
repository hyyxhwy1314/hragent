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

复用阿里云 MaaS 兼容端点，模型 `text-embedding-v3`（1024 维），与对话模型**共用** `ai.base-url` / `ai.api-key`，仅模型名不同，配置项为 `ai.embedding.model.name`（环境变量 `AI_EMBEDDING_MODEL_NAME`）。

### 检索链路

```
查询向量化 ──► Qdrant top-k 相似度检索（带 visibility 权限预过滤）
          ──► 返回带来源文档引用的片段 ──► 主模型据此作答
```

片段带 `visibility` 元数据做权限感知：

| 取值 | 可见范围 |
|------|---------|
| PUBLIC | 全员可见 |
| HR | 仅 HR/HRBP/ADMIN 可见 |

### 内置知识文档与索引入库

内置知识文档位于 `src/main/resources/knowledge/*.md`，共 6 篇（考勤、请假、转正、绩效、薪酬、行为规范）。应用启动时**幂等**切分并向量化入库（MySQL 已有片段则跳过）。

重建索引：将 `hragent.rag.reindex` 设为 `true` 并重启即可（会清空旧片段与 Qdrant 集合并重新向量化）。

### 配置项 `hragent.rag`

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `enabled` | true | 是否启用知识库检索 |
| `top-k` | 5 | 检索返回片段数 |
| `min-score` | 0.35 | 相似度下限 |
| `chunk-size` | 400 | 切分块大小 |
| `chunk-overlap` | 80 | 切分块重叠 |
| `reindex` | false | 是否重建索引 |
| `qdrant.{host,port,collection,vector-dim}` | — | Qdrant 连接与集合、向量维度 |

增量建表脚本 `src/main/resources/sql/rag_knowledge_ddl.sql` 需**手动执行**。核心代码位于 `agent/rag/`：`KnowledgeRetrievalService`、`KnowledgeBaseInitializer`、`QdrantVectorClient`、`RagProperties`、`TextSplitter`。

## 工具路由

工具选择采用分层 Tool Routing：注册表 → 确定性硬过滤 → 粗分类 → BM25 召回 Top-K → 模型精选 → 执行前鉴权。代码位于 `agent/routing/`（`ToolRouter` 顶部有中文链路注释）。

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