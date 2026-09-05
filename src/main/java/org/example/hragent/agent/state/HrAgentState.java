package org.example.hragent.agent.state;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HR Agent 状态（用于 LangGraph 工作流）
 * 管理 Agent 图中各节点之间的共享状态
 * <p>
 * 核心改进：引入 CHAT_MEMORY_ID 关联 LangChain4j 的 ChatMemory，
 * 对话上下文由 LangChain4j MessageWindowChatMemory 统一管理。
 */
public class HrAgentState extends AgentState {
    
    // 状态键
    public static final String MESSAGES_KEY = "messages";
    public static final String TOOL_RESULTS_KEY = "toolResults";
    public static final String ERROR_MESSAGE_KEY = "errorMessage";
    public static final String SESSION_ID_KEY = "sessionId";
    public static final String USER_ID_KEY = "userId";
    public static final String CHAT_MEMORY_ID_KEY = "chatMemoryId";

    /** 待执行的工具调用请求列表（模型节点下发，动作节点消费，last-wins 覆盖） */
    public static final String TOOL_CALLS_KEY = "toolCalls";
    /** 当前工具循环轮次（防死循环，last-wins 覆盖） */
    public static final String ITERATION_KEY = "iteration";
    /** 本回合累计输入(Token)，last-wins 覆盖（各模型帧累加） */
    public static final String INPUT_TOKENS_KEY = "inputTokens";
    /** 本回合累计输出(Token)，last-wins 覆盖 */
    public static final String OUTPUT_TOKENS_KEY = "outputTokens";
    /** 本回合累计工具调用次数，last-wins 覆盖 */
    public static final String TOOL_CALL_COUNT_KEY = "toolCallCount";
    
    /** 用户消息前缀 */
    public static final String USER_PREFIX = "[user] ";
    /** 助手消息前缀 */
    public static final String ASSISTANT_PREFIX = "[assistant] ";
    
    // 状态 schema，使用 appender 通道
    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry(MESSAGES_KEY, Channels.appender(ArrayList::new)),
            Map.entry(TOOL_RESULTS_KEY, Channels.appender(ArrayList::new)),
            Map.entry(ERROR_MESSAGE_KEY, Channels.appender(ArrayList::new)),
            Map.entry(SESSION_ID_KEY, Channels.appender(ArrayList::new)),
            Map.entry(USER_ID_KEY, Channels.appender(ArrayList::new)),
            Map.entry(CHAT_MEMORY_ID_KEY, Channels.appender(ArrayList::new)),
            // 循环控制：last-wins（每次节点用新值覆盖旧值）
            Map.entry(TOOL_CALLS_KEY, Channels.base((List<ToolCallRecord> prev, List<ToolCallRecord> next) -> next)),
            Map.entry(ITERATION_KEY, Channels.base((Integer prev, Integer next) -> next)),
            // 累计统计：last-wins，节点在写入时会基于已有值做累加
            Map.entry(INPUT_TOKENS_KEY, Channels.base((Integer prev, Integer next) -> next)),
            Map.entry(OUTPUT_TOKENS_KEY, Channels.base((Integer prev, Integer next) -> next)),
            Map.entry(TOOL_CALL_COUNT_KEY, Channels.base((Integer prev, Integer next) -> next))
    );
    
    public HrAgentState(Map<String, Object> initData) {
        super(initData);
    }
    
    // 状态值访问方法
    public List<String> messages() {
        return this.<List<String>>value(MESSAGES_KEY)
                .orElse(List.of());
    }
    
    public String toolResults() {
        List<String> results = this.<List<String>>value(TOOL_RESULTS_KEY).orElse(List.of());
        return results.isEmpty() ? "" : results.get(results.size() - 1);
    }
    
    public String errorMessage() {
        List<String> errors = this.<List<String>>value(ERROR_MESSAGE_KEY).orElse(List.of());
        return errors.isEmpty() ? null : errors.get(errors.size() - 1);
    }
    
    public String sessionId() {
        List<String> sessionIds = this.<List<String>>value(SESSION_ID_KEY).orElse(List.of());
        return sessionIds.isEmpty() ? null : sessionIds.get(sessionIds.size() - 1);
    }
    
    public String userId() {
        List<String> userIds = this.<List<String>>value(USER_ID_KEY).orElse(List.of());
        return userIds.isEmpty() ? null : userIds.get(userIds.size() - 1);
    }
    
    /**
     * 获取 LangChain4j ChatMemory 的 ID（用于 per-session 对话记忆）
     */
    public Long chatMemoryId() {
        List<String> ids = this.<List<String>>value(CHAT_MEMORY_ID_KEY).orElse(List.of());
        if (ids.isEmpty()) return null;
        try {
            return Long.parseLong(ids.get(ids.size() - 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 当前待执行的工具调用请求列表（模型节点下发）
     */
    @SuppressWarnings("unchecked")
    public List<ToolCallRecord> toolCalls() {
        return this.<List<ToolCallRecord>>value(TOOL_CALLS_KEY).orElse(List.of());
    }
    
    /**
     * 当前工具循环轮次（从 0 开始）
     */
    public int currentIteration() {
        return this.<Integer>value(ITERATION_KEY).orElse(0);
    }

    /** 本回合累计输入 Token */
    public int inputTokens() {
        return this.<Integer>value(INPUT_TOKENS_KEY).orElse(0);
    }

    /** 本回合累计输出 Token */
    public int outputTokens() {
        return this.<Integer>value(OUTPUT_TOKENS_KEY).orElse(0);
    }

    /** 本回合累计工具调用次数 */
    public int toolCallCount() {
        return this.<Integer>value(TOOL_CALL_COUNT_KEY).orElse(0);
    }
    
    // 状态判断辅助方法
    public boolean hasError() {
        return errorMessage() != null;
    }
}
