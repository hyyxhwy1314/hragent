# HR Agent AI 交互数据看板 · 大数据链路

完整链路：**MySQL（t_agent_interact_log）→ Debezium → Kafka → Flink SQL → ClickHouse → 前端看板**

## 1. 数据源确认
后端已把每次 AI 对话回合的统计写入 `t_agent_interact_log`（session_id、token、工具次数、耗时、错误等）。
对应建表语句在 `src/main/resources/sql/init_schema.sql`（第 14 张表）。

## 2. 启动大数据环境
```bash
cd deploy-bigdata
docker compose up -d
```
等待 MySQL / Kafka / Debezium connect / Flink / ClickHouse / Superset 全部 healthy。

## 3. 注册 Debezium connector（把表变更推送到 Kafka）
```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @connect/ai-interact-connector.json
```
验证：
```bash
curl http://localhost:8083/connectors/ai-interact-connector/status
```

## 4. 初始化 ClickHouse 表
```bash
docker exec -i bd-clickhouse clickhouse-client --password clickhouse < clickhouse/01_ai_dashboard.sql
```

## 5. 提交 Flink SQL 作业（Kafka -> ClickHouse 明细同步）
打开 Flink WebUI http://localhost:8081 ，在 SQL client 中执行
`flink/sql/01_ai_dashboard.sql`；或在容器内：
```bash
docker exec -it bd-flink-jobmanager /opt/flink/bin/sql-client.sh
```

## 6. 前端看板
走 `/ai-dashboard` 页面，聚合接口：
- `GET /agent/dashboard/summary`
- `GET /agent/dashboard/daily`
- `GET /agent/dashboard/tool-distribution`

> 说明：后端聚合接口当前直接查询业务库 MySQL（保证看板即时可用）。
> ClickHouse 明细/聚合表可在此基础上提供离线大历史分析与 Superset 可视化增强。