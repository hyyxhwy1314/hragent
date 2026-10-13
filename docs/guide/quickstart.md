# 快速开始

## 环境要求

- JDK 17+
- Node.js 18+
- Maven 3.8+
- MySQL 5.7+
- Redis 6.2+
- Qdrant（向量库，端口 6333/6334）
- Docker（可选，推荐用于启动基础设施）

## 一分钟启动

### 1. 克隆项目

```bash
git clone https://github.com/hyyxhwy1314/hragent.git
cd hragent
```

### 2. 启动基础设施（Docker）

本项目不含 `docker-compose` 文件，基础设施由以下三个容器提供，请按项目现有容器启动：

- douban-mysql（端口 13306）
- douban-redis（端口 16379）
- douban-qdrant（端口 6333 / 6334）

其中 Qdrant 向量库可使用：

```bash
docker run -d --name douban-qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant:latest
```

### 3. 初始化数据库

数据库建表脚本位于 `src/main/resources/sql/init_schema.sql`，启动时 Flowable 会自动创建 `act_*` 系列表。

项目**不使用自动迁移**，`src/main/resources/sql/` 下的脚本需**手动执行**，现有：`init_schema.sql`、`flow_enhance_ddl.sql`、`employee_self_service_ddl.sql`、`rag_knowledge_ddl.sql`（知识库片段表 `t_knowledge_chunk`）。

### 4. 配置环境变量

在 `src/main/resources/application.yaml` 中配置，或通过环境变量注入：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:13306/hr_agent_db
    username: root
    password: Admin@888888
  data:
    redis:
      host: localhost
      port: 16379
      password: 123

ai:
  api-key: ${AI_API_KEY}
  model: qwen3.7-flash
  embedding:
    model:
      name: text-embedding-v3
```

### 5. 启动后端

```bash
mvn spring-boot:run
```

后端默认启动在 `http://localhost:8080`。

### 6. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认启动在 `http://localhost:5173`。

## 首次使用

1. 打开浏览器访问 `http://localhost:5173`
2. 默认登录账号在 `t_employee` 表中初始化
3. 进入 AI 助手页面，输入 "查询所有员工" 体验智能交互