# HR-Agent

> 智能人力资源管理平台 — AI 驱动的全栈 HR 管理系统

[![文档站点](https://img.shields.io/badge/docs-hragent-blue?style=flat-square&logo=readthedocs)](https://hyyxhwy1314.github.io/hragent/)
[![GitHub last commit](https://img.shields.io/github/last-commit/hyyxhwy1314/hragent?style=flat-square)](https://hyyxhwy1314.github.io/hragent/)
[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://hyyxhwy1314.github.io/hragent/)
[![Spring Boot 3](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?style=flat-square&logo=spring-boot&logoColor=white)](https://hyyxhwy1314.github.io/hragent/)
[![Vue 3](https://img.shields.io/badge/Vue_3-3.5-4FC08D?style=flat-square&logo=vue.js&logoColor=white)](https://hyyxhwy1314.github.io/hragent/)
[![MySQL](https://img.shields.io/badge/MySQL-5.7-4479A1?style=flat-square&logo=mysql&logoColor=white)](https://hyyxhwy1314.github.io/hragent/)
[![Redis](https://img.shields.io/badge/Redis-6.2-DC382D?style=flat-square&logo=redis&logoColor=white)](https://hyyxhwy1314.github.io/hragent/)

---

## 项目简介

HR-Agent 是一个**AI 驱动的智能人力资源管理平台**，后端采用 Spring Boot 3 + Java 17，前端采用 Vue 3 + TypeScript + Ant Design Vue。系统深度融合 AI 大模型能力，提供自然语言交互的 HR 操作体验，并集成 Flowable 工作流引擎实现流程自动化审批。

## 核心功能

### 🤖 AI 智能助手

自然语言驱动的 HR 操作助手，支持对话式查询员工、简历、审批进度等，动态工具加载节省 Token

### 📚 知识库智能问答（RAG）

基于向量库的知识检索增强问答，覆盖考勤、请假、转正、绩效、薪酬、行为规范等制度条款，回答附带来源文档引用，并按可见范围（全员 / HR）做权限过滤

### 🗂️ 员工自助服务

员工可自助发起请假、在职证明等申请，减少线下流转

### 👤 员工管理

员工信息 CRUD，支持多角色权限体系（员工/主管/HR/HRBP/管理员）

### 📄 简历管理

候选人简历管理，AI 解析结构化 JSON，智能标签匹配与评分

### 📋 流程审批

Flowable 工作流引擎驱动入职/转正/调岗/离职四类审批流程，并支持请假、证明、会签等增强流程，全程轨迹追踪

### 📊 绩效管理

员工绩效记录与评估管理

### 📚 培训管理

培训课程目录管理与能力标签匹配推荐

### 📈 数据看板

工作台仪表盘，含指标卡片、Token 消耗趋势图、活动日志


## 技术栈

| 类别 | 技术 |
|------|------|
| **后端** | Java 17、Spring Boot 3.3、MyBatis-Plus 3.5、Flowable 7.0、Redis + Redisson |
| **前端** | Vue 3.5、TypeScript 5.6、Ant Design Vue 4、ECharts 6、Pinia |
| **数据库** | MySQL 5.7+、Redis 6.2+、Qdrant |
| **AI** | 对接阿里云 MaaS / OpenRouter 大模型 API，SSE 流式对话、LangChain4j、Qdrant 向量库、text-embedding-v3 向量化 |
| **构建** | Maven、Vite、Docker |
| **其他** | JWT 鉴权、AOP 切面、阿里云 OCR、邮件通知 |

## 快速开始

### 前置要求

- JDK 17+
- Node.js 22+
- Docker（用于启动 MySQL、Redis、Qdrant 容器）
- Maven 3.8+

### 启动基础设施

本项目不含 `docker-compose` 文件，基础设施由以下三个容器提供：

| 容器 | 镜像 | 宿主机端口 |
|------|------|-----------|
| douban-mysql | mysql:8.0 | 13306 → 3306 |
| douban-redis | redis:7-alpine | 16379 → 6379（密码 `123`） |
| douban-qdrant | qdrant/qdrant | 6333（REST） / 6334（gRPC） |

`douban-mysql`、`douban-redis` 按项目现有容器启动；Qdrant 向量库可使用：

```bash
docker run -d --name douban-qdrant -p 6333:6333 -p 6334:6334 qdrant/qdrant:latest
```

### 启动后端

```bash
# 构建
mvn clean package -DskipTests

# 启动（需要配置 AI_API_KEY 环境变量）
java -jar target/hr-agent-0.0.1-SNAPSHOT.jar
```

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

> 默认登录：工号 + 密码 `123456`
> 详细部署文档请参考 [部署指南](https://hyyxhwy1314.github.io/hragent/#/deploy/guide) 和 [快速开始](https://hyyxhwy1314.github.io/hragent/#/guide/quickstart)

## 项目结构

```
hr-agent/
├── src/main/java/org/example/hragent/
│   ├── agent/          # AI 智能体（模型调用、工具管理、对话，含 rag/ 向量检索与 routing/ 工具路由）
│   ├── controller/     # REST API 控制器
│   ├── service/        # 业务逻辑层
│   ├── mapper/         # MyBatis 数据访问
│   ├── entity/         # 数据实体
│   ├── config/         # 配置类
│   ├── annotation/     # 自定义注解
│   ├── aspect/         # AOP 切面（缓存/分布式锁/限流）
│   └── utils/          # 工具类
├── frontend/           # Vue 3 + TypeScript 前端
│   └── src/
│       ├── views/      # 页面组件
│       ├── api/        # API 接口封装
│       ├── router/     # 路由配置
│       └── layouts/    # 布局组件
├── docs/               # Docsify 文档站点
├── openwiki/           # OpenWiki 自动生成文档索引
└── scripts/            # 辅助脚本
```

## 后端模块

- `agent/`
- `annotation/`
- `aspect/`
- `config/`
- `constant/`
- `controller/`
- `converter/`
- `dto/`
- `entity/`
- `exception/`
- `interceptor/`
- `listener/`
- `mapper/`
- `service/`
- `utils/`
- `vo/`

## 前端页面

- `abilityTag/`
- `agent/`
- `aiDashboard/`
- `architecture/`
- `contractReview/`
- `dashboard/`
- `employee/`
- `flow/`
- `jobPost/`
- `login/`
- `performance/`
- `resume/`
- `resumeAbilityRel/`
- `todo/`
- `trainingCourse/`

## 文档

- [📖 文档中心](https://hyyxhwy1314.github.io/hragent/) — 完整的用户手册与 API 文档
- [🚀 快速开始](https://hyyxhwy1314.github.io/hragent/#/guide/quickstart) — 环境搭建与运行指南
- [🏗️ 系统架构](https://hyyxhwy1314.github.io/hragent/#/guide/architecture) — 整体架构设计
- [🔌 API 接口](https://hyyxhwy1314.github.io/hragent/#/api/overview) — REST API 参考

## 环境变量

| 变量 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `AI_API_KEY` | 是 | — | AI 模型 API 密钥 |
| `AI_BASE_URL` | 否 | 阿里云 MaaS 地址 | AI 服务地址 |
| `AI_MODEL_NAME` | 否 | qwen3.7-flash | 模型名称 |
| `AI_EMBEDDING_MODEL_NAME` | 否 | text-embedding-v3 | 向量化模型名（与对话模型共用 `ai.base-url` / `ai.api-key`） |
| `FEISHU_MCP_APP_ID` | 否 | — | 飞书远程 MCP 应用 ID |
| `FEISHU_MCP_APP_SECRET` | 否 | — | 飞书远程 MCP 应用密钥 |
| `MAIL_PASSWORD` | 否 | — | QQ SMTP 授权码 |
| `COS_ACCESS_KEY` | 否 | — | 对象存储密钥 |

## 许可证

[MIT](LICENSE)

---

> 文档自动生成于 2026-10-13 | [OpenWiki](https://hyyxhwy1314.github.io/hragent/) 驱动
