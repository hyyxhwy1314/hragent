package org.example.hragent.agent.graph;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.example.hragent.agent.persistence.AgentStatePersistenceService;
import org.example.hragent.agent.state.HrAgentState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 调度器 - 统一调度中心
 * Agent 执行的主入口，协调整个工作流的执行，
 * 同时在 MySQL 中持久化会话元数据、对话历史与工具调用日志
 */
@Service
public class AgentScheduler {
    
    private final HrAgentGraph hrAgentGraph;
    private final AgentStatePersistenceService persistenceService;
    private CompiledGraph<HrAgentState> compiledGraph;
    
    public AgentScheduler(HrAgentGraph hrAgentGraph,
                         AgentStatePersistenceService persistenceService) {
        this.hrAgentGraph = hrAgentGraph;
        this.persistenceService = persistenceService;
        try {
            this.compiledGraph = hrAgentGraph.buildGraph().compile();
        } catch (GraphStateException e) {
            throw new RuntimeException("HR Agent 工作流图编译失败", e);
        }
    }
    
    /**
     * 通过 Agent 工作流处理用户消息
     * 
     * @param userMessage 用户输入的消息
     * @param userId 用户 ID（可选，用于个性化）
     * @param sessionId 会话 ID（可选，用于保持会话连续性）
     * @return Agent 的响应
     */
    public String processMessage(String userMessage, String userId, String sessionId) {
        try {
            // 若未提供会话 ID，则自动生成
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = UUID.randomUUID().toString();
            }
            
            boolean isNewSession = !persistenceService.hasState(sessionId);
            
            // 加载已有状态或创建新状态
            Map<String, Object> stateData = loadOrCreateStateData(sessionId, userId, userMessage);
            
            // 记录执行前的已有消息数量（用于增量持久化，不含本次新增的用户消息）
            int preMessageCount = (int) stateData.getOrDefault("PRE_EXECUTION_MESSAGE_COUNT", 0);
            
            // 执行 Agent 工作流
            var finalStateOpt = compiledGraph.invoke(stateData);
            if (finalStateOpt.isEmpty()) {
                throw new RuntimeException("Agent 工作流返回空状态");
            }
            
            // 保存最终状态到 Redis
            HrAgentState finalState = finalStateOpt.get();
            persistenceService.saveState(sessionId, finalState);
            
            // 持久化到 MySQL（仅增量消息）
            persistToDatabase(sessionId, userId, userMessage, isNewSession, finalState, preMessageCount);
            
            // 提取并返回最终响应
            return extractFinalResponse(finalState);
            
        } catch (Exception e) {
            return "处理您的请求时出错：" + e.getMessage();
        }
    }
    
    /**
     * 处理用户消息并自动管理会话
     * 
     * @param userMessage 用户输入的消息
     * @param userId 用户 ID（可选）
     * @return Agent 的响应和会话 ID
     */
    public AgentResponse processMessageWithSession(String userMessage, String userId) {
        String sessionId = UUID.randomUUID().toString();
        String response = processMessage(userMessage, userId, sessionId);
        return new AgentResponse(response, sessionId);
    }
    
    /**
     * 继续已有会话
     * 
     * @param userMessage 用户的后续消息
     * @param sessionId 已有的会话 ID
     * @return Agent 的响应
     */
    public String continueConversation(String userMessage, String sessionId) {
        return processMessage(userMessage, null, sessionId);
    }
    
    /**
     * 清除会话（从 Redis 和 MySQL 中彻底删除）
     * 
     * @param sessionId 需要清除的会话 ID
     */
    public void clearSession(String sessionId) {
        persistenceService.deleteState(sessionId);
        persistenceService.hardDeleteSession(sessionId);
    }
    
    /**
     * 加载已有状态数据，若不存在则创建新状态数据
     */
    private Map<String, Object> loadOrCreateStateData(String sessionId, String userId, String userMessage) {
        HrAgentState existingState = persistenceService.loadState(sessionId);
        
        Map<String, Object> stateData;
        int existingSize = 0;
        
        if (existingState == null) {
            // 创建新状态数据，使用空列表初始化
            stateData = new HashMap<>();
            stateData.put(HrAgentState.MESSAGES_KEY, new ArrayList<>());
            stateData.put(HrAgentState.INTENT_KEY, new ArrayList<>());
            stateData.put(HrAgentState.TOOL_RESULTS_KEY, new ArrayList<>());
            stateData.put(HrAgentState.VALIDATION_STATUS_KEY, new ArrayList<>());
            stateData.put(HrAgentState.ERROR_MESSAGE_KEY, new ArrayList<>());
            stateData.put(HrAgentState.SESSION_ID_KEY, new ArrayList<>());
            stateData.put(HrAgentState.USER_ID_KEY, new ArrayList<>());
            stateData.put(HrAgentState.RETRY_COUNT_KEY, new ArrayList<>());
            
            // 初始化默认值
            ((ArrayList<String>) stateData.get(HrAgentState.SESSION_ID_KEY)).add(sessionId);
            if (userId != null) {
                ((ArrayList<String>) stateData.get(HrAgentState.USER_ID_KEY)).add(userId);
            }
            ((ArrayList<Integer>) stateData.get(HrAgentState.RETRY_COUNT_KEY)).add(0);
            existingSize = 0;
        } else {
            // 使用已有状态数据
            stateData = new HashMap<>(existingState.data());
            // 获取已有消息数量（记录在添加用户消息之前）
            List<String> msgs = existingState.messages();
            existingSize = msgs.size();
        }
        
        // 将新的用户消息添加到消息列表中
        @SuppressWarnings("unchecked")
        ArrayList<Object> messages = (ArrayList<Object>) stateData.get(HrAgentState.MESSAGES_KEY);
        if (messages == null) {
            messages = new ArrayList<>();
            stateData.put(HrAgentState.MESSAGES_KEY, messages);
        }
        messages.add(HrAgentState.USER_PREFIX + userMessage);
        
        // 将已有消息大小存入 stateData，后续取出用于增量持久化
        stateData.put("PRE_EXECUTION_MESSAGE_COUNT", existingSize);
        
        return stateData;
    }
    
    /**
     * 将对话数据持久化到 MySQL
     */
    private void persistToDatabase(String sessionId, String userIdStr, String userMessage,
                                   boolean isNewSession, HrAgentState finalState, int preMessageCount) {
        try {
            // 解析 userId
            Long userId = null;
            if (userIdStr != null && !userIdStr.isEmpty()) {
                try {
                    userId = Long.parseLong(userIdStr);
                } catch (NumberFormatException ignored) {}
            }
            
            // 新会话时创建 MySQL 会话记录，标题取首条用户消息的前20字
            if (isNewSession) {
                String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
                persistenceService.createSession(sessionId, userId, title);
            }
            
            // 仅保存增量消息（本次新增的部分）
            List<String> messages = finalState.messages();
            if (messages != null && messages.size() > preMessageCount) {
                List<String> newMessages = messages.subList(preMessageCount, messages.size());
                persistenceService.saveMessages(sessionId, newMessages);
            }
            
        } catch (Exception e) {
            // 持久化异常不影响主流程
            System.err.println("持久化对话记录到 MySQL 失败：" + e.getMessage());
        }
    }
    
    /**
     * 从 Agent 状态中提取最终响应
     */
    private String extractFinalResponse(HrAgentState state) {
        // 从消息列表中获取最后一条 AI 消息
        var messages = state.messages();
        if (messages.isEmpty()) {
            return "未生成响应。";
        }
        
        // 返回最后一条消息内容（去掉前缀）
        var lastMessage = messages.get(messages.size() - 1);
        return stripPrefix(lastMessage);
    }
    
    private String stripPrefix(String msg) {
        if (msg.startsWith(HrAgentState.USER_PREFIX)) {
            return msg.substring(HrAgentState.USER_PREFIX.length());
        }
        if (msg.startsWith(HrAgentState.ASSISTANT_PREFIX)) {
            return msg.substring(HrAgentState.ASSISTANT_PREFIX.length());
        }
        return msg;
    }
    
    /**
     * 响应包装类
     */
    public static class AgentResponse {
        private final String response;
        private final String sessionId;
        
        public AgentResponse(String response, String sessionId) {
            this.response = response;
            this.sessionId = sessionId;
        }
        
        public String getResponse() {
            return response;
        }
        
        public String getSessionId() {
            return sessionId;
        }
    }
}