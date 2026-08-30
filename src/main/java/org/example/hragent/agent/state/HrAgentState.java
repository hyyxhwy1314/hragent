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
    
    /** 用户消息前缀 */
    public static final String USER_PREFIX = "[user] ";
    /** 助手消息前缀 */
    public static final String ASSISTANT_PREFIX = "[assistant] ";
    
    // 状态 schema，使用 appender 通道
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES_KEY, Channels.appender(ArrayList::new),
            TOOL_RESULTS_KEY, Channels.appender(ArrayList::new),
            ERROR_MESSAGE_KEY, Channels.appender(ArrayList::new),
            SESSION_ID_KEY, Channels.appender(ArrayList::new),
            USER_ID_KEY, Channels.appender(ArrayList::new),
            CHAT_MEMORY_ID_KEY, Channels.appender(ArrayList::new)
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
    
    // 状态判断辅助方法
    public boolean hasError() {
        return errorMessage() != null;
    }
}
