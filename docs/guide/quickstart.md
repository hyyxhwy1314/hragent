# 快速开始

## 环境要求

- JDK 17+
- Node.js 18+
- Maven 3.8+
- MySQL 5.7+
- Redis 6.2+
- Docker（可选，推荐用于启动基础设施）

## 一分钟启动

### 1. 克隆项目

```bash
git clone https://github.com/hyyxhwy1314/hragent.git
cd hragent
```

### 2. 启动基础设施（Docker）

```bash
docker-compose up -d
```

这会启动：
- MySQL 5.7（端口 13306）
- Redis 6.2（端口 16379）

### 3. 初始化数据库

数据库建表脚本位于 `src/main/resources/sql/init_schema.sql`，启动时 Flowable 会自动创建 `act_*` 系列表。

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
  model: deepseek-v4-flash-0731
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