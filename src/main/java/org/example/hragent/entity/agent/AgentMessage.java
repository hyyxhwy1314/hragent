package org.example.hragent.entity.agent;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.example.hragent.entity.BaseEntity;

/**
 * 对话历史表
 * 记录 Agent 与用户之间的每条对话消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_message")
public class AgentMessage extends BaseEntity {

    /** 会话 ID（t_agent_session.session_id） */
    @TableField("session_id")
    private String sessionId;

    /** 角色: user / assistant */
    @TableField("role")
    private String role;

    /** 消息内容 */
    @TableField("content")
    private String content;

    /** 消息类型: text / intent / tool_result / validation / error */
    @TableField("message_type")
    private String messageType;

    /** 扩展元数据（JSON 格式） */
    @TableField("metadata_json")
    private String metadataJson;
}