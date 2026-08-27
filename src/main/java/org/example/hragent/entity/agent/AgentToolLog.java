package org.example.hragent.entity.agent;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.example.hragent.entity.BaseEntity;

/**
 * 工具调用日志表
 * 记录 Agent 每次工具调用的输入、输出、耗时等信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_tool_log")
public class AgentToolLog extends BaseEntity {

    /** 会话 ID（t_agent_session.session_id） */
    @TableField("session_id")
    private String sessionId;

    /** 关联的消息 ID（t_agent_message.id，可为空） */
    @TableField("message_id")
    private Long messageId;

    /** 触发工具调用的意图编码 */
    @TableField("intent_code")
    private String intentCode;

    /** 工具名称 */
    @TableField("tool_name")
    private String toolName;

    /** 输入参数 */
    @TableField("input_params")
    private String inputParams;

    /** 输出结果 */
    @TableField("output_result")
    private String outputResult;

    /** 调用状态: success / error / timeout */
    @TableField("status")
    private String status;

    /** 执行耗时（毫秒） */
    @TableField("duration_ms")
    private Long durationMs;

    /** 错误信息 */
    @TableField("error_message")
    private String errorMessage;
}