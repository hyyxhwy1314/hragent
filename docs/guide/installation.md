# 安装部署

## 生产环境依赖

| 组件 | 版本 | 说明 |
|------|------|------|
| MySQL | 5.7+ | 业务数据库，建议 8.0 |
| Redis | 6.2+ | 缓存与分布式锁 |
| Qdrant | latest | 向量库，知识库检索（RAG），端口 6333/6334 |
| Java | 17+ | 后端运行环境 |
| Node.js | 18+ | 前端构建环境 |

## 构建部署

### 后端构建

```bash
# 编译打包
mvn clean package -DskipTests

# 启动
java -jar target/hr-agent-0.0.1-SNAPSHOT.jar
```

### 前端构建

```bash
cd frontend
npm install
npm run build
```

构建产物在 `frontend/dist/` 目录，可部署到 Nginx 或直接使用后端静态资源服务。

## Docker 部署

### docker-compose.yml

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:5.7
    ports:
      - "13306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: Admin@888888
      MYSQL_DATABASE: hr_agent_db
    volumes:
      - ./sql/init_schema.sql:/docker-entrypoint-initdb.d/init.sql

  redis:
    image: redis:7-alpine
    ports:
      - "16379:6379"
    command: redis-server --requirepass 123

  qdrant:
    image: qdrant/qdrant:latest
    ports:
      - "6333:6333"
      - "6334:6334"

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      AI_API_KEY: ${AI_API_KEY}
    depends_on:
      - mysql
      - redis
```

> 项目不使用自动迁移，建表脚本位于 `src/main/resources/sql/`，需**手动执行**（含 `init_schema.sql`、`flow_enhance_ddl.sql`、`employee_self_service_ddl.sql`、`rag_knowledge_ddl.sql`）。

## 环境变量配置

| 变量名 | 说明 | 必填 |
|--------|------|------|
| `AI_API_KEY` | AI 模型 API Key | 是 |
| `AI_BASE_URL` | AI 服务地址 | 否 |
| `AI_MODEL_NAME` | 对话模型名称 | 否 |
| `AI_EMBEDDING_MODEL_NAME` | 向量化模型名（默认 `text-embedding-v3`） | 否 |
| `FEISHU_MCP_APP_ID` | 飞书远程 MCP 应用 ID | 否 |
| `FEISHU_MCP_APP_SECRET` | 飞书远程 MCP 应用密钥 | 否 |
| `FEISHU_APP_ID` | 飞书审批卡片应用 ID（为空时复用 `FEISHU_MCP_APP_ID`） | 否 |
| `FEISHU_APP_SECRET` | 飞书审批卡片应用密钥（为空时复用 `FEISHU_MCP_APP_SECRET`） | 否 |
| `SPRING_DATASOURCE_URL` | 数据库连接地址 | 否 |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 | 否 |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | 否 |
| `SPRING_REDIS_HOST` | Redis 地址 | 否 |
| `SPRING_REDIS_PASSWORD` | Redis 密码 | 否 |
| `AI_ROUTING_ENABLED` | 是否启用本地决策模型做工具路由分类（默认 `false`） | 否 |
| `AI_ROUTING_BASE_URL` | 本地决策模型地址（默认 `http://127.0.0.1:11434/v1`） | 否 |
| `AI_ROUTING_MODEL_NAME` | 本地决策模型名（默认 `qwen2.5:3b-instruct`） | 否 |
| `AI_ROUTING_TIMEOUT_SECONDS` | 本地决策模型调用超时秒数（默认 `20`） | 否 |
| `AI_ROUTING_PLANNING_ENABLED` | 是否启用本地模型对只读工具做选工具+生成参数（默认 `false`，需先开启 `AI_ROUTING_ENABLED`） | 否 |
| `AI_ROUTING_LOCAL_PRIMARY` | 全量本地化：把 Agent 主模型切为本地模型（默认 `false`）；远程 API 模型 Bean 与凭据保留不删，关闭即切回 | 否 |
| `AI_ROUTING_LLM_TIMEOUT_SECONDS` | 全量本地化时本地主模型的超时秒数（默认 `300`） | 否 |

## 本地决策模型（可选，默认关闭）

工具路由的「业务域分类」可由本地轻量模型承担，最终作答与工具参数生成仍由远程模型负责。
运行时需提供 OpenAI 兼容端点，程序与模型均部署在本机 D 盘；默认关闭，调用失败/超时/输出非法时自动回退规则分类，不影响主链路。

推荐 `llama.cpp`：CPU 精简包仅约 19MB，无独立显卡的机器同样适用，实测本机单次分类约 0.4 秒。

```powershell
# 1) 运行时：下载 llama-bXXXX-bin-win-cpu-x64.zip 后解压到 D:\llama
Expand-Archive D:\llama\llama-cpu.zip -DestinationPath D:\llama -Force

# 2) 模型：走国内镜像 hf-mirror.com 下载 GGUF 到 D:\llama\models
curl.exe -L -o D:\llama\models\qwen2.5-3b-instruct-q4_k_m.gguf `
  https://hf-mirror.com/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf

# 3) 启动，监听 127.0.0.1:11434（与 ai.routing.base-url 默认值一致）
D:\llama\llama-server.exe -m D:\llama\models\qwen2.5-3b-instruct-q4_k_m.gguf `
  --host 127.0.0.1 --port 11434 --ctx-size 4096 --threads 8
```

若改用 Ollama：`ollama serve`（同样监听 11434）后 `ollama pull qwen2.5:3b-instruct`，并把 `OLLAMA_MODELS` 指向 D 盘，其余配置不变。

```powershell
# 启用本地决策模型后重启后端
$env:AI_ROUTING_ENABLED = 'true'

# （可选）进一步让本地模型接管「只读工具」的选工具 + 生成参数，命中时跳过远程模型首跳（更省 token）
$env:AI_ROUTING_PLANNING_ENABLED = 'true'
```

模型能力不足时的取舍：本地模型只输出「业务域标签集合」这一短结构化结果，属于影响面最小的决策点，
无法承担时把 `AI_ROUTING_ENABLED` 置为 `false` 即可一键回退到规则分类。

只读工具规划（`AI_ROUTING_PLANNING_ENABLED`）：本地模型仅对只读/查询类候选工具规划，
写操作与流程类工具仍由远程模型选定；规划调用失败、超时、输出非法或命中候选外工具时自动交回远程模型自选，
因此同样可一键回退（把 `AI_ROUTING_PLANNING_ENABLED` 置为 `false` 即可，行为与启用前完全一致）。

## 飞书审批卡片（可选）

配置后，审批待办会自动推送到审批人的飞书单聊；审批人点击卡片上的「同意 / 拒绝」即可直接驱动流程流转。
审批人手机号/邮箱解析不到飞书账号、或未配置凭据时自动降级，不影响 Web 端审批。

### 1. 飞书开放平台配置

1. 在[飞书开放平台](https://open.feishu.cn/)创建企业自建应用，获取 `App ID` / `App Secret`；
2. 开通应用身份权限：
   - `im:message:send`：发送单聊消息（推送审批卡片）；
   - `contact:user.id:readonly`：通过手机号或邮箱获取用户 ID（解析 `open_id`）。
3. 在「事件与回调 → 回调订阅」中订阅 `card.action.trigger`（卡片回传交互），
   订阅方式选择「使用长连接接收回调」——无需公网地址，后端启动时自动建立 WebSocket 长连接；
4. 确保审批人员工的手机号是其飞书账号绑定的手机号（邮箱仅作兜底，可能不是登录邮箱）。

### 2. 后端配置

与云文档 MCP 复用同一个自建应用时，凭据只需配置一次（`hragent.feishu` 默认复用 `hragent.mcp.feishu`）：

```yaml
hragent:
  mcp:
    feishu:
      enabled: true
      app-id: ${FEISHU_MCP_APP_ID:}
      app-secret: ${FEISHU_MCP_APP_SECRET:}
  feishu:
    enabled: true
    app-id: "${FEISHU_APP_ID:${hragent.mcp.feishu.app-id:}}"
    app-secret: "${FEISHU_APP_SECRET:${hragent.mcp.feishu.app-secret:}}"
```

本地开发可写入 `src/main/resources/application-local.yaml`（已被 `.gitignore` 忽略，**禁止提交到仓库**）：

```yaml
hragent:
  mcp:
    feishu:
      app-id: cli_xxxxxxxxxxxxxxxx
      app-secret: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

### 3. 启动验证

后端启动日志应出现以下两行，表示长连接已建立：

```
飞书审批卡片回调长连接已启动
connected to wss://msg-frontier.feishu.cn/ws/v2?...
```

审批卡片推送成功时会打印：

```
审批卡片推送成功 taskId=..., node=..., openId=ou_...
```

### 4. 注意事项

- **同一应用只运行一个后端实例**：飞书按长连接分发事件，多实例并存时回调会随机落到不同实例，
  容易出现「点击偶发无响应」；联调/生产请只保留一个长连接实例。
- 卡片按钮内嵌审批人 `open_id`，回调会校验操作人身份，转发卡片给他人无法代审批。
- 任务已处理时点击会返回「已处理」提示并替换卡片，不会重复流转。
- 若日志出现 `审批人未匹配到飞书账号`，检查员工手机号是否为真实飞书绑定手机号，以及
  `contact:user.id:readonly` 是否为**应用身份**权限。