# 安装部署

## 生产环境依赖

| 组件 | 版本 | 说明 |
|------|------|------|
| MySQL | 5.7+ | 业务数据库，建议 8.0 |
| Redis | 6.2+ | 缓存与分布式锁 |
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
    image: redis:6.2
    ports:
      - "16379:6379"
    command: redis-server --requirepass 123

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

## 环境变量配置

| 变量名 | 说明 | 必填 |
|--------|------|------|
| `AI_API_KEY` | AI 模型 API Key | 是 |
| `SPRING_DATASOURCE_URL` | 数据库连接地址 | 否 |
| `SPRING_DATASOURCE_USERNAME` | 数据库用户名 | 否 |
| `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | 否 |
| `SPRING_REDIS_HOST` | Redis 地址 | 否 |
| `SPRING_REDIS_PASSWORD` | Redis 密码 | 否 |