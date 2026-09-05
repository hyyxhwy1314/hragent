-- ============================================================
-- HR Agent AI 交互数据看板：Flink SQL 实时聚合作业
-- 链路：MySQL CDC(Debezium) -> Kafka topic hr_agent_db.hr_agent_db.t_agent_interact_log
--      -> Flink 解析 -> ClickHouse 明细表 -> 聚合指标
--
-- 使用说明（在 flink sql-client 或 Flink WebUI 中执行）：
--   1. 先确保 Debezium connector 已把 t_agent_interact_log 的变更发到 Kafka
--   2. 已创建 ClickHouse 表 bd_ai_interact_log（见 clickhouse/01_ai_dashboard.sql）
--   3. 将下方的 kafka/clickhouse 连接命名并按实际环境替换主机
-- ============================================================

-- 1) Kafka 源表：Debezium 以 JsonConverter 输出，topic 名为 {server}.{db}.{table}
CREATE TABLE kafka_ai_interact (
    `id`           BIGINT,
    `session_id`   STRING,
    `user_id`      BIGINT,
    `user_message` STRING,
    `answer`       STRING,
    `tool_used`    INT,
    `tool_call_count` INT,
    `input_tokens` INT,
    `output_tokens` INT,
    `duration_ms`  BIGINT,
    `has_error`    INT,
    `create_time`  TIMESTAMP(3),
    `is_deleted`   INT,
    PRIMARY KEY (`id`) NOT ENFORCED
) WITH (
    'connector' = 'kafka',
    'topic'     = 'hr_agent_db.hr_agent_db.t_agent_interact_log',
    'properties.bootstrap.servers' = 'kafka:9092',
    'properties.group.id'          = 'ai-dashboard-flink',
    'scan.startup.mode'            = 'earliest-offset',
    'format'                       = 'debezium-json'
);

-- 2) ClickHouse 目标表
CREATE TABLE ch_ai_interact (
    `id`           UInt64,
    `session_id`   String,
    `user_id`      Nullable(Int64),
    `user_message` String,
    `answer`       String,
    `tool_used`    UInt8,
    `tool_call_count` Int32,
    `input_tokens` Int32,
    `output_tokens` Int32,
    `duration_ms`  Int64,
    `has_error`    UInt8,
    `create_time`  TIMESTAMP(3),
    `is_deleted`   UInt8
) WITH (
    'connector' = 'clickhouse',
    'url'       = 'clickhouse://clickhouse:8123',
    'username'  = 'default',
    'password'  = 'clickhouse',
    'database-name' = 'hr_olap',
    'table-name'    = 'bd_ai_interact_log',
    'sink.batch-size' = '1000',
    'sink.flush-interval' = '1000'
);

-- 3) 全量同步作业（不强制 upsert，明细追加）
INSERT INTO ch_ai_interact
SELECT
    CAST(`id` AS UInt64),
    `session_id`,
    `user_id`,
    `user_message`,
    `answer`,
    CAST(`tool_used` AS UInt8),
    `tool_call_count`,
    `input_tokens`,
    `output_tokens`,
    `duration_ms`,
    CAST(`has_error` AS UInt8),
    `create_time`,
    CAST(`is_deleted` AS UInt8)
FROM kafka_ai_interact;

-- 4) 每日聚合宽表（可选）：按自然日预聚合，供看板快速查询
-- CREATE TABLE ch_ai_daily AS
-- SELECT toDate(create_time) AS day,
--        count(*) AS total_count,
--        sum(tool_used) AS tool_used_count,
--        sum(input_tokens) AS total_input_tokens,
--        sum(output_tokens) AS total_output_tokens,
--        avg(duration_ms) AS avg_duration_ms,
--        sum(has_error) AS error_count
-- FROM ch_ai_interact
-- GROUP BY toDate(create_time);