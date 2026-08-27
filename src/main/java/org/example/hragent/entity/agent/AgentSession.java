package org.example.hragent.entity.agent;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.example.hragent.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * Agent 会话表
 * 记录每次 Agent 对话的会话元数据，与 LangGraph 状态持久化关联
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_agent_session")
public class AgentSession extends BaseEntity {

    /** 会话唯一标识（UUID） */
    @TableField("session_id")
    private String sessionId;

    /** 用户 ID（t_employee.id） */
    @TableField("user_id")
    private Long userId;

    /** 会话状态: 0-进行中 1-已结束 2-已过期 */
    @TableField("status")
    private Integer status;

    /** 当前/最后识别的意图编码 */
    @TableField("intent")
    private String intent;

    /** 会话开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 会话结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 会话标题（首条用户消息摘要） */
    @TableField("title")
    private String title;

    /** 消息总数 */
    @TableField("message_count")
    private Integer messageCount;

    /** 备注 */
    @TableField("remark")
    private String remark;
}