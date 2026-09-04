package org.example.hragent.entity.agent;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.example.hragent.entity.BaseEntity;

/**
 * AI 交互日志表
 * <p>
 * 记录 Agent 每次对话回合的交互统计信息（用户提问、回答、Token 消耗、
 * 工具调用次数、耗时等），用于 AI 数据看板统计分析。作为大数据链路的
 * MySQL CDC 源，经 Debezium → Kafka → Flink 聚合后写入 ClickHouse。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_interact_log")
public class AgentInteractLog extends BaseEntity {

    /** 会话 ID（t_agent_session.session_id） */
    @TableField("session_id")
    private String sessionId;

    /** 用户 ID（t_employee.id） */
    @TableField("user_id")
    private Long userId;

    /** 用户提问内容 */
    @TableField("user_message")
    private String userMessage;

    /** 助手最终回答 */
    @TableField("answer")
    private String answer;

    /** 是否调用了工具：0-否 1-是 */
    @TableField("tool_used")
    private Integer toolUsed;

    /** 本回合工具调用次数 */
    @TableField("tool_call_count")
    private Integer toolCallCount;

    /** 输入（Prompt）Token 数 */
    @TableField("input_tokens")
    private Integer inputTokens;

    /** 输出（Completion）Token 数 */
    @TableField("output_tokens")
    private Integer outputTokens;

    /** 本回合总耗时（毫秒） */
    @TableField("duration_ms")
    private Long durationMs;

    /** 是否命中错误/兜底：0-正常 1-错误 */
    @TableField("has_error")
    private Integer hasError;
}