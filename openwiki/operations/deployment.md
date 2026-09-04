---
type: concept
title: Deployment and Operations
description: Deploying the HR Agent application using Docker Compose for infrastructure, Maven for the backend, and Vite for the frontend. Covers environment configuration, database initialization, and production deployment patterns.
tags: [deployment, docker, operations, infrastructure, environment]
verified:
  - by: openwiki/0.5.0
    at: 2026-09-04T11:44:31.911Z
sources:
  - id: openwiki-source-8af68d003d35a6bd2bcb8421
    resource: repo://docs/deploy/guide.md
  - id: openwiki-source-e36e67a69d6c98d8fa4382df
    resource: repo://docs/guide/installation.md
  - id: openwiki-source-378e3cf05ab0d05d335c68d5
    resource: repo://frontend/vite.config.ts
  - id: openwiki-source-52cc9e318cad03f04699d7ae
    resource: repo://src/main/java/org/example/hragent/config/DataInitializer.java
  - id: openwiki-source-da0cfc5c3214f2e5c7288665
    resource: repo://src/main/java/org/example/hragent/HrAgentApplication.java
  - id: openwiki-source-e543b55a9b54e13df8badad4
    resource: repo://src/main/resources/application.yaml
  - id: openwiki-source-b5f4d7dd7e822ced7ea7ef90
    resource: repo://src/main/resources/sql/init_schema.sql
generated: { by: "openwiki/0.5.0", at: "2026-09-04T11:44:31.911Z" }
---

# Deployment and Operations

The HR Agent application is a full-stack system consisting of a Spring Boot backend, a Vue 3 + TypeScript frontend, and supporting infrastructure services (MySQL and Redis). Deployment proceeds in three stages: infrastructure startup, backend build and launch, and frontend build and serve.

## Infrastructure Prerequisites

The application depends on two external services that must be available before the backend starts:

| Service | Version | Default Port | Container Name | Purpose |
|---------|---------|-------------|---------------|---------|
| MySQL | 5.7+ | 13306 | `douban-mysql` | Business database (`hr_agent_db`) |
| Redis | 6.2+ | 16379 | `douban-redis` | Caching and distributed locking |

Other optional services (RabbitMQ, Elasticsearch, object storage) are configured in `application.yaml` but are not required for core functionality.

## Docker Compose Deployment

Docker Compose is the primary mechanism for starting infrastructure services. The project does not include a multi-container backend Dockerfile; instead, it mounts `src/main/resources/sql/init_schema.sql` into the MySQL container to initialize the schema on first boot.

```bash
# Start MySQL and Redis containers
docker-compose up -d
```

The default `docker-compose.yml` defines:

- **MySQL 5.7** on port `13306` with root password `Admin@888888` and database `hr_agent_db`. The init script at `src/main/resources/sql/init_schema.sql` runs automatically on first startup.
- **Redis 6.2** on port `16379` with password `123`.

### Docker Desktop Data Migration (Windows)

On Windows hosts running Docker Desktop with large WSL data volumes, a helper script moves the Docker data directory to a larger drive:

```powershell
# migrate-docker.ps1
# 1. Copies settings-store.json with DataFolder=D:\DockerData
# 2. Verifies D:\DockerData\wsl files exist
# 3. Backs up the original wsl folder with a timestamp
# 4. Shuts down WSL so Docker Desktop can use the new path
```

This script is a Windows-host housekeeping utility, not part of the application's deployment pipeline.

## Database Initialization

### Schema Creation

Business tables are created by `src/main/resources/sql/init_schema.sql`, which runs as a Docker entrypoint init script. The schema defines 14 tables:

- `t_employee` — employee records with role-based access (EMPLOYEE, DEPT_LEADER, HR, HRBP, ADMIN)
- `t_job_post` — recruitment positions
- `t_resume` — candidate resumes with AI-parsed structured JSON
- `t_flow_instance` — business process instances binding to Flowable engine
- `t_flow_approval` — approval records for business processes
- `t_performance` — employee performance records
- `t_training_course` — training course catalog
- `t_ability_tag` — skill tag dictionary for resume matching
- `t_resume_ability_rel` — many-to-many resume-to-tag relationships
- `t_sys_file` — file metadata for object storage references
- `t_agent_session` — AI agent conversation sessions
- `t_agent_message` — conversation message history
- `t_agent_tool_log` — tool invocation logs
- `t_agent_interact_log` — per-turn AI interaction statistics for analytics pipelines

All tables share the BaseEntity contract: `id`, `create_time`, `update_time`, `delete_time`, and `is_deleted` (logical delete flag).

### Flowable Workflow Tables

Flowable engine tables (`ACT_*`) are not included in the init script. The engine creates them automatically on first boot because `flowable.database-schema-update` is set to `true` in `application.yaml`. The Flowable migration script at `src/main/resources/sql/flowable_ddl.sql` adds supplemental columns (`role`, `leader_id`, `password`) to `t_employee` and performance indexes to `t_flow_instance` if they do not already exist.

### Default Credentials

All employee accounts in `init_schema.sql` have `password` set to `NULL`. On backend startup, the `DataInitializer` component (`config/DataInitializer.java`) detects employees with null passwords and sets them to the BCrypt hash of the default password `123456`. This eliminates the need to store hardcoded password hashes in SQL.

Default login: employee number (`emp_no`) + password `123456`.

## Backend Deployment

### Build

```bash
mvn clean package -DskipTests
```

The output artifact is `target/hr-agent-0.0.1-SNAPSHOT.jar`.

### Startup

```bash
java -jar target/hr-agent-0.0.1-SNAPSHOT.jar
```

The backend listens on port `8080`. `HrAgentApplication.java` is the single entrypoint annotated with `@SpringBootApplication`, `@EnableCaching`, and `@EnableAspectJAutoProxy`.

### Configuration

Configuration is centralized in `src/main/resources/application.yaml`. Key sections:

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:13306/hr_agent_db
    username: root
    password: Admin@888888
  data:
    redis:
      host: 127.0.0.1
      port: 16379
      password: 123

ai:
  api-key: ${AI_API_KEY:}
  base-url: ${AI_BASE_URL:https://llm-8sr4ofm58q8qaj4e.cn-beijing.maas.aliyuncs.com/compatible-mode/v1}
  model:
    name: ${AI_MODEL_NAME:deepseek-v4-flash-0731}

flowable:
  database-schema-update: true
  async-executor-activate: true
  history-level: full
```

### Required Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `AI_API_KEY` | Yes | — | AI model API key |
| `AI_BASE_URL` | No | Aliyun MaaS endpoint | AI service base URL |
| `AI_MODEL_NAME` | No | `deepseek-v4-flash-0731` | Model name |
| `MAIL_PASSWORD` | No | — | QQ SMTP authorization code for onboarding email notifications |
| `COS_ACCESS_KEY` / `COS_SECRET_KEY` | No | — | Object storage credentials for resume file storage |
| `ALIYUN_OCR_ACCESS_KEY_ID` / `ALIYUN_OCR_ACCESS_KEY_SECRET` | No | — | Aliyun OCR credentials for scanned resume parsing |

### Production JVM Recommendations

```bash
java -Xms512m -Xmx1024m \
  -Dspring.profiles.active=prod \
  -jar hr-agent-0.0.1-SNAPSHOT.jar
```

HikariCP is used for the database connection pool with default settings (sufficient for moderate load). Redis caching is enabled via `@EnableCaching` with configurable TTL to prevent cache stampede.

## Frontend Deployment

### Build

```bash
cd frontend
npm install
npm run build
```

The production artifact is written to `frontend/dist/`. `vite.config.ts` configures the development proxy to forward `/api` requests to `http://localhost:8080`.

### Development Mode

```bash
npm run dev   # Vite dev server on http://localhost:5173
```

### Production Serve

The `frontend/dist/` directory can be served by any static file server. The recommended approach is Nginx:

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /var/www/hragent/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # SSE streaming for AI chat
    location /agent/chat/stream {
        proxy_pass http://localhost:8080;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }
}
```

## Architecture at Runtime

<!-- openwiki: mermaid parse failed and this diagram was converted to a text fence so it does not break rendering. Fix the diagram source and restore the mermaid fence. Parser error: Heuristic: an unescaped angle bracket inside a label breaks rendering; rephrase the label. -->
```text
flowchart LR
    Browser["Browser<br/>(port 5173 dev / Nginx prod)"]
    Nginx["Nginx<br/>(reverse proxy)"]
    SpringBoot["Spring Boot<br/>(port 8080)"]
    MySQL["MySQL 5.7<br/>(port 13306)"]
    Redis["Redis 6.2<br/>(port 16379)"]
    AIModel["AI Model Service<br/>(external)"]

    Browser -->|HTTP| Nginx
    Nginx -->|/api/*| SpringBoot
    SpringBoot -->|queries| MySQL
    SpringBoot -->|cache/lock| Redis
    SpringBoot -->|AI inference| AIModel

    style Nginx fill:#f96,stroke:#333
    style SpringBoot fill:#6c5ce7,color:#fff,stroke:#333
    style MySQL fill:#00758f,color:#fff,stroke:#333
    style Redis fill:#d63031,color:#fff,stroke:#333
    style AIModel fill:#00b894,color:#fff,stroke:#333
```

*Services and traffic paths in a running deployment.*

## Monitoring and Observability

Logging levels in `application.yaml`:

```yaml
logging:
  level:
    root: INFO
    org.example.hragent: DEBUG
    com.baomidou.mybatisplus: WARN
    org.flowable: WARN
    org.apache.ibatis: WARN
```

Production monitoring recommendations:

- **Application metrics**: Spring Boot Actuator endpoints exposed on an internal port, scraped by Prometheus and visualized in Grafana
- **Log aggregation**: Forward logs to ELK Stack (Elasticsearch, Logstash, Kibana) for centralized search and alerting
- **Alerting**: Configure webhook alerts to DingTalk or Feishu bots for error rate thresholds

## Data Hygiene Utilities

Windows PowerShell scripts in the repository root provide Docker Desktop maintenance utilities and are not part of the application's runtime deployment:

- `cleanup_docker.ps1` — removes residual WSL backup directories from `C:\Users\Administrator\AppData\Local\Docker`
- `check_c_docker.ps1` — reports the size of Docker Desktop data on the C: drive
- `migrate-docker.ps1` — relocates the Docker WSL data folder to `D:\DockerData`

These scripts target the Windows host environment and should be run on the Docker Desktop host machine when disk space on the system drive becomes constrained.
