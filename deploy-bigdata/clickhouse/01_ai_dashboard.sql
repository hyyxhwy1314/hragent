-- ============================================================
-- HR Agent AI 交互数据看板：ClickHouse 建表
-- 前缀 bd_ 表示来自业务库 t_agent_interact_log 的 CDC 同步数据
-- 说明：Flink SQL 将 Kafka(Byte 格式) 中的 Debezium 变更解析后写入本表
-- ============================================================

DROP TABLE IF EXISTS bd_ai_interact_log;
CREATE TABLE bd_ai_interact_log (
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
    `create_time`  DateTime('Asia/Shanghai'),
    `is_deleted`   UInt8
) ENGINE = MergeTree
PARTITION BY toYYYYMM(create_time)
ORDER BY (create_time, id)
TTL toDateTime(create_time) + INTERVAL 180 DAY;