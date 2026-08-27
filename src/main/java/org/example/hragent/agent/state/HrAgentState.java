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
 * 消息存储为纯字符串，以 "[user]" 或 "[assistant]" 前缀区分角色
 */
public class HrAgentState extends AgentState {
    
    // 状态键
    public static final String MESSAGES_KEY = "messages";
    public static final String INTENT_KEY = "intent";
    public static final String TOOL_RESULTS_KEY = "toolResults";
    public static final String VALIDATION_STATUS_KEY = "validationStatus";
    public static final String ERROR_MESSAGE_KEY = "errorMessage";
    public static final String SESSION_ID_KEY = "sessionId";
    public static final String USER_ID_KEY = "userId";
    public static final String RETRY_COUNT_KEY = "retryCount";
    
    /** 用户消息前缀 */
    public static final String USER_PREFIX = "[user] ";
    /** 助手消息前缀 */
    public static final String ASSISTANT_PREFIX = "[assistant] ";
    
    // 状态 schema，全部使用 appender 通道以简化处理
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES_KEY, Channels.appender(ArrayList::new),
            INTENT_KEY, Channels.appender(ArrayList::new),
            TOOL_RESULTS_KEY, Channels.appender(ArrayList::new),
            VALIDATION_STATUS_KEY, Channels.appender(ArrayList::new),
            ERROR_MESSAGE_KEY, Channels.appender(ArrayList::new),
            SESSION_ID_KEY, Channels.appender(ArrayList::new),
            USER_ID_KEY, Channels.appender(ArrayList::new),
            RETRY_COUNT_KEY, Channels.appender(ArrayList::new)
    );
    
    public HrAgentState(Map<String, Object> initData) {
        super(initData);
    }
    
    // 状态值访问方法
    public List<String> messages() {
        return this.<List<String>>value(MESSAGES_KEY)
                .orElse(List.of());
    }
    
    public String intent() {
        List<String> intents = this.<List<String>>value(INTENT_KEY).orElse(List.of());
        return intents.isEmpty() ? null : intents.get(intents.size() - 1);
    }
    
    public String toolResults() {
        List<String> results = this.<List<String>>value(TOOL_RESULTS_KEY).orElse(List.of());
        return results.isEmpty() ? "" : results.get(results.size() - 1);
    }
    
    public String validationStatus() {
        List<String> statuses = this.<List<String>>value(VALIDATION_STATUS_KEY).orElse(List.of());
        return statuses.isEmpty() ? "pending" : statuses.get(statuses.size() - 1);
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
    
    public int retryCount() {
        List<Integer> retryCounts = this.<List<Integer>>value(RETRY_COUNT_KEY).orElse(List.of());
        return retryCounts.isEmpty() ? 0 : retryCounts.get(retryCounts.size() - 1);
    }
    
    // 状态判断辅助方法
    public boolean hasError() {
        return errorMessage() != null;
    }
    
    public boolean isValidated() {
        return "validated".equals(validationStatus());
    }
    
    public boolean canRetry() {
        return retryCount() < 3; // 最多重试 3 次
    }
}
