# HR-Agent 智能人力资源管理平台

> 基于 AI Agent 的智能化人力资源管理平台，打通人才招聘、员工管理、绩效考评、培训发展全流程。

## 核心功能

| 模块 | 功能 | 说明 |
|------|------|------|
| **AI 智能助手** | 自然语言交互 | 通过对话式 AI 完成查询、操作、分析，无需手动点菜单 |
| **员工管理** | 员工档案全生命周期 | 入职、离职、调岗、转正，支持 Flowable 工作流审批 |
| **简历管理** | 简历库与招聘流程 | 简历录入、筛选、面试、录用，状态流转跟踪 |
| **流程审批** | 自定义工作流引擎 | 基于 Flowable 的 BPMN 2.0 流程引擎，支持多级审批 |
| **绩效管理** | 绩效考评与追踪 | 目标设定、考核评分、结果归档 |
| **培训管理** | 培训课程与能力标签 | 课程管理、能力标签匹配、推荐培训 |
| **数据看板** | 系统数据可视化 | 实时统计、趋势分析、Token 消耗监控 |

## 技术栈

| 层次 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.3 + Java 17 |
| ORM | MyBatis-Plus 3.5 |
| 工作流引擎 | Flowable 7.0 |
| AI Agent | LangChain4j + LangGraph4j |
| 数据库 | MySQL 5.7+ |
| 缓存 | Redis 6.2 + Redisson |
| 前端 | Vue 3 + Ant Design Vue 4 |
| 构建工具 | Maven + Vite |

## 快速开始

```bash
# 1. 克隆项目
git clone https://github.com/hyyxhwy1314/hragent.git

# 2. 启动基础设施（MySQL + Redis）
docker-compose up -d

# 3. 配置环境变量
# 设置 AI_API_KEY、数据库连接等

# 4. 启动后端
mvn spring-boot:run

# 5. 启动前端
cd frontend && npm install && npm run dev
```

> 文档站点由 GitHub Actions 自动部署，每次推送 `docs/` 目录变更到 master 即自动更新。

> 详细步骤请查看 [快速开始指南](/guide/quickstart.md)。