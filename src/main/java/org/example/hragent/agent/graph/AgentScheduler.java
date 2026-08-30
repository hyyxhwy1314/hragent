package org.example.hragent.agent.graph;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.example.hragent.agent.persistence.AgentStatePersistenceService;
import org.example.hragent.agent.state.HrAgentState;
import org.example.hragent.agent.tools.HrAgentAiServiceFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 调度器 - 统一调度中心
 * <p>
 * 以 LangChain4j 为核心的架构：
 * <ul>
 *     <li>ChatMemory — 由 HrAgentAiServiceFactory 管理，每个会话独立的对话记忆</li>
 *     <li>AiService — 自动处理工具调用、多轮对话、上下文管理</li>
 *     <li>LangGraph4j — 轻量级流程编排（START → tool → answer → END）</li>
 * </ul>
 * 调度器负责：会话管理、状态持久化（Redis + MySQL）、ChatMemory 生命周期
 */
@Service
public class AgentScheduler {
    
    private final HrAgentGraph hrAgentGraph;
    private final AgentStatePersistenceService persistenceService;
    private final HrAgentAiServiceFactory aiServiceFactory;
    private CompiledGraph<HrAgentState> compiledGraph;
    
    public AgentScheduler(HrAgentGraph hrAgentGraph,
                         AgentStatePersistenceService persistenceService,
                         HrAgentAiServiceFactory aiServiceFactory) {
        this.hrAgentGraph = hrAgentGraph;
        this.persistenceService = persistenceService;
        this.aiServiceFactory = aiServiceFactory;
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
            
            // 生成稳定的 numeric ChatMemory ID（LangChain4j ChatMemory 要求 Long 类型）
            Long chatMemoryId = generateChatMemoryId(sessionId);
            
            // 加载已有状态或创建新状态
            Map<String, Object> stateData = loadOrCreateStateData(sessionId, userId, chatMemoryId);
            
            // 记录执行前的消息数量（用于增量持久化）
            int preMessageCount = getPreExecutionMessageCount(stateData);
            
            // 将用户消息添加到状态（ToolInvocationNode 会从中提取）
            addUserMessage(stateData, userMessage);
            
            // 执行 Agent 工作流
            // LangChain4j AiService 内部自动完成：
            // ChatMemory加载历史 → LLM推理 → @Tool调用 → LLM生成回答 → ChatMemory更新
            var finalStateOpt = compiledGraph.invoke(stateData);
            if (finalStateOpt.isEmpty()) {
                throw new RuntimeException("Agent 工作流返回空状态");
            }
            
            // 保存最终状态到 Redis
            HrAgentState finalState = finalStateOpt.get();
            persistenceService.saveState(sessionId, finalState);
            
            // 持久化增量消息到 MySQL
            persistToDatabase(sessionId, userId, userMessage, isNewSession, finalState, preMessageCount);
            
            // 提取并返回最终响应
            return extractFinalResponse(finalState);
            
        } catch (Exception e) {
            return "处理您的请求时出错：" + e.getMessage();
        }
    }
    
    /**
     * 处理用户消息并自动管理会话
     */
    public AgentResponse processMessageWithSession(String userMessage, String userId) {
        String sessionId = UUID.randomUUID().toString();
        String response = processMessage(userMessage, userId, sessionId);
        return new AgentResponse(response, sessionId);
    }
    
    /**
     * 继续已有会话
     */
    public String continueConversation(String userMessage, String sessionId) {
        return processMessage(userMessage, null, sessionId);
    }
    
    /**
     * 清除指定会话（同时清理 Redis、MySQL 和 LangChain4j ChatMemory）
     */
    public void clearSession(String sessionId) {
        // 清理 LangChain4j ChatMemory
        Long chatMemoryId = generateChatMemoryId(sessionId);
        aiServiceFactory.clearSession(chatMemoryId);
        
        // 清理 Redis 和 MySQL
        persistenceService.deleteState(sessionId);
        persistenceService.hardDeleteSession(sessionId);
    }
    
    // ==================== 内部方法 ====================
    
    /**
     * 从 sessionId 生成稳定的 numeric ChatMemory ID
     */
    private Long generateChatMemoryId(String sessionId) {
        long hash = 0;
        for (int i = 0; i < sessionId.length(); i++) {
            hash = 31 * hash + sessionId.charAt(i);
        }
        return Math.abs(hash);
    }
    
    /**
     * 获取执行前的消息数量
     */
    private int getPreExecutionMessageCount(Map<String, Object> stateData) {
        Object count = stateData.get("PRE_EXECUTION_MESSAGE_COUNT");
        return count instanceof Integer ? (Integer) count : 0;
    }
    
    /**
     * 加载已有状态数据，若不存在则创建新状态数据
     */
    private Map<String, Object> loadOrCreateStateData(String sessionId, String userId, Long chatMemoryId) {
        HrAgentState existingState = persistenceService.loadState(sessionId);
        
        Map<String, Object> stateData;
        int existingSize = 0;
        
        if (existingState == null) {
            // 创建新状态数据
            stateData = new HashMap<>();
            stateData.put(HrAgentState.MESSAGES_KEY, new ArrayList<>());
            stateData.put(HrAgentState.TOOL_RESULTS_KEY, new ArrayList<>());
            stateData.put(HrAgentState.ERROR_MESSAGE_KEY, new ArrayList<>());
            stateData.put(HrAgentState.SESSION_ID_KEY, new ArrayList<>());
            stateData.put(HrAgentState.USER_ID_KEY, new ArrayList<>());
            stateData.put(HrAgentState.CHAT_MEMORY_ID_KEY, new ArrayList<>());
            
            // 初始化默认值
            ((ArrayList<String>) stateData.get(HrAgentState.SESSION_ID_KEY)).add(sessionId);
            if (userId != null) {
                ((ArrayList<String>) stateData.get(HrAgentState.USER_ID_KEY)).add(userId);
            }
            ((ArrayList<String>) stateData.get(HrAgentState.CHAT_MEMORY_ID_KEY))
                    .add(String.valueOf(chatMemoryId));
            existingSize = 0;
        } else {
            // 使用已有状态数据
            stateData = new HashMap<>(existingState.data());
            List<String> msgs = existingState.messages();
            existingSize = msgs.size();
        }
        
        // 记录执行前的消息数量（用于增量持久化）
        stateData.put("PRE_EXECUTION_MESSAGE_COUNT", existingSize);
        
        return stateData;
    }
    
    /**
     * 将用户消息添加到状态的消息列表中
     */
    @SuppressWarnings("unchecked")
    private void addUserMessage(Map<String, Object> stateData, String userMessage) {
        ArrayList<Object> messages = (ArrayList<Object>) stateData.get(HrAgentState.MESSAGES_KEY);
        if (messages == null) {
            messages = new ArrayList<>();
            stateData.put(HrAgentState.MESSAGES_KEY, messages);
        }
        messages.add(HrAgentState.USER_PREFIX + userMessage);
    }
    
    /**
     * 将增量对话数据持久化到 MySQL
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
            
            // 新会话时创建 MySQL 会话记录
            if (isNewSession) {
                String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
                persistenceService.createSession(sessionId, userId, title);
            }
            
            // 仅保存增量消息（本次工作流执行新增的部分）
            List<String> messages = finalState.messages();
            if (messages != null && messages.size() > preMessageCount) {
                List<String> newMessages = messages.subList(preMessageCount, messages.size());
                persistenceService.saveMessages(sessionId, newMessages);
            }
            
        } catch (Exception e) {
            System.err.println("持久化对话记录到 MySQL 失败：" + e.getMessage());
        }
    }
    
    /**
     * 从 Agent 状态中提取最终响应
     */
    private String extractFinalResponse(HrAgentState state) {
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
        
        public String getResponse() { return response; }
        public String getSessionId() { return sessionId; }
    }
}
