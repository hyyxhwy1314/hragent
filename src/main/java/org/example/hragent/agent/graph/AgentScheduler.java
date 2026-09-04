package org.example.hragent.agent.graph;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.example.hragent.agent.controller.AgentController.ChatResponse;
import org.example.hragent.agent.controller.AgentController.ChatResponse.ToolCallStep;
import org.example.hragent.agent.persistence.AgentStatePersistenceService;
import org.example.hragent.agent.state.HrAgentState;
import org.example.hragent.agent.state.ToolCallRecord;
import org.example.hragent.agent.tools.HrAgentAiServiceFactory;
import org.example.hragent.entity.agent.AgentInteractLog;
import org.example.hragent.mapper.agent.AgentInteractLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final HrAgentGraph hrAgentGraph;
    private final AgentStatePersistenceService persistenceService;
    private final HrAgentAiServiceFactory aiServiceFactory;
    private final AgentInteractLogMapper interactLogMapper;
    private CompiledGraph<HrAgentState> compiledGraph;

    public AgentScheduler(HrAgentGraph hrAgentGraph,
                          AgentStatePersistenceService persistenceService,
                          HrAgentAiServiceFactory aiServiceFactory,
                          AgentInteractLogMapper interactLogMapper) {
        this.hrAgentGraph = hrAgentGraph;
        this.persistenceService = persistenceService;
        this.aiServiceFactory = aiServiceFactory;
        this.interactLogMapper = interactLogMapper;
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
     * @return Agent 的响应（含思考过程）
     */
    public ChatResponse processMessage(String userMessage, String userId, String sessionId) {
        long startMs = System.currentTimeMillis();
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
            
            // 将用户消息添加到状态（ModelThinkNode 会从中提取）
            addUserMessage(stateData, userMessage);
            
            // 执行 Agent 工作流
            var finalStateOpt = compiledGraph.invoke(stateData);
            if (finalStateOpt.isEmpty()) {
                throw new RuntimeException("Agent 工作流返回空状态");
            }
            
            // 保存最终状态到 Redis
            HrAgentState finalState = finalStateOpt.get();
            persistenceService.saveState(sessionId, finalState);
            
            // 持久化增量消息到 MySQL
            persistToDatabase(sessionId, userId, userMessage, isNewSession, finalState, preMessageCount);
            
            // 提取最终响应和思考过程
            String response = extractFinalResponse(finalState);
            String thinking = extractThinking(finalState);
            List<ToolCallStep> toolSteps = extractToolSteps(finalState);
            int inputTokens = finalState.inputTokens();
            int outputTokens = finalState.outputTokens();

            // 记录本回合交互统计
            saveInteractLog(sessionId, userId, userMessage, response, finalState, startMs);

            return new ChatResponse(response, thinking, toolSteps, sessionId, inputTokens, outputTokens);
            
        } catch (Exception e) {
            return new ChatResponse("处理您的请求时出错：" + e.getMessage(), sessionId);
        }
    }

    /**
     * 记录一次完整对话回合的交互统计日志
     */
    private void saveInteractLog(String sessionId, String userId, String userMessage,
                                 String answer, HrAgentState finalState, long startMs) {
        try {
            AgentInteractLog log = new AgentInteractLog();
            log.setSessionId(sessionId);
            log.setUserId(userId != null ? Long.valueOf(userId) : null);
            log.setUserMessage(truncate(userMessage, 500));
            log.setAnswer(truncate(answer, 2000));
            int toolCount = finalState.toolCallCount();
            log.setToolCallCount(toolCount);
            log.setToolUsed(toolCount > 0 ? 1 : 0);
            log.setInputTokens(finalState.inputTokens());
            log.setOutputTokens(finalState.outputTokens());
            log.setDurationMs(System.currentTimeMillis() - startMs);
            log.setHasError(finalState.hasError() ? 1 : 0);
            interactLogMapper.insert(log);
        } catch (Exception ignored) {
            // 交互日志记录失败不影响主流程
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 处理用户消息并自动管理会话
     */
    public ChatResponse processMessageWithSession(String userMessage, String userId) {
        String sessionId = UUID.randomUUID().toString();
        return processMessage(userMessage, userId, sessionId);
    }

    /**
     * 继续已有会话
     */
    public ChatResponse continueConversation(String userMessage, String sessionId) {
        return processMessage(userMessage, null, sessionId);
    }

    /**
     * 清除指定会话（同时清理 Redis、MySQL 和 LangChain4j ChatMemory）
     */
    public void clearSession(String sessionId) {
        Long chatMemoryId = generateChatMemoryId(sessionId);
        aiServiceFactory.clearSession(chatMemoryId);
        persistenceService.deleteState(sessionId);
        persistenceService.hardDeleteSession(sessionId);
    }

    // ==================== 内部方法 ====================

    private Long generateChatMemoryId(String sessionId) {
        long hash = 0;
        for (int i = 0; i < sessionId.length(); i++) {
            hash = 31 * hash + sessionId.charAt(i);
        }
        return Math.abs(hash);
    }

    private int getPreExecutionMessageCount(Map<String, Object> stateData) {
        Object count = stateData.get("PRE_EXECUTION_MESSAGE_COUNT");
        return count instanceof Integer ? (Integer) count : 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadOrCreateStateData(String sessionId, String userId, Long chatMemoryId) {
        HrAgentState existingState = persistenceService.loadState(sessionId);
        
        Map<String, Object> stateData;
        int existingSize = 0;
        
        if (existingState == null) {
            stateData = new HashMap<>();
            stateData.put(HrAgentState.MESSAGES_KEY, new ArrayList<>());
            stateData.put(HrAgentState.TOOL_RESULTS_KEY, new ArrayList<>());
            stateData.put(HrAgentState.ERROR_MESSAGE_KEY, new ArrayList<>());
            stateData.put(HrAgentState.SESSION_ID_KEY, new ArrayList<>());
            stateData.put(HrAgentState.USER_ID_KEY, new ArrayList<>());
            stateData.put(HrAgentState.CHAT_MEMORY_ID_KEY, new ArrayList<>());
            
            ((ArrayList<String>) stateData.get(HrAgentState.SESSION_ID_KEY)).add(sessionId);
            if (userId != null) {
                ((ArrayList<String>) stateData.get(HrAgentState.USER_ID_KEY)).add(userId);
            }
            ((ArrayList<String>) stateData.get(HrAgentState.CHAT_MEMORY_ID_KEY))
                    .add(String.valueOf(chatMemoryId));
            existingSize = 0;
        } else {
            stateData = new HashMap<>(existingState.data());
            List<String> msgs = existingState.messages();
            existingSize = msgs.size();
        }
        
        stateData.put("PRE_EXECUTION_MESSAGE_COUNT", existingSize);
        stateData.put(HrAgentState.ITERATION_KEY, 0);

        return stateData;
    }

    @SuppressWarnings("unchecked")
    private void addUserMessage(Map<String, Object> stateData, String userMessage) {
        ArrayList<Object> messages = (ArrayList<Object>) stateData.get(HrAgentState.MESSAGES_KEY);
        if (messages == null) {
            messages = new ArrayList<>();
            stateData.put(HrAgentState.MESSAGES_KEY, messages);
        }
        messages.add(HrAgentState.USER_PREFIX + userMessage);
    }

    private void persistToDatabase(String sessionId, String userIdStr, String userMessage,
                                   boolean isNewSession, HrAgentState finalState, int preMessageCount) {
        try {
            Long userId = null;
            if (userIdStr != null && !userIdStr.isEmpty()) {
                try {
                    userId = Long.parseLong(userIdStr);
                } catch (NumberFormatException ignored) {}
            }
            
            if (isNewSession) {
                String title = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
                persistenceService.createSession(sessionId, userId, title);
            }
            
            List<String> messages = finalState.messages();
            if (messages != null && messages.size() > preMessageCount) {
                List<String> newMessages = messages.subList(preMessageCount, messages.size());
                persistenceService.saveMessages(sessionId, newMessages);
            }
            
        } catch (Exception e) {
            System.err.println("持久化对话记录到 MySQL 失败：" + e.getMessage());
        }
    }

    private String extractFinalResponse(HrAgentState state) {
        var messages = state.messages();
        if (messages.isEmpty()) {
            return "未生成响应。";
        }
        var lastMessage = messages.get(messages.size() - 1);
        return stripPrefix(lastMessage);
    }

    /**
     * 从状态中提取思考过程
     * 包含所有工具调用摘要，按步骤排列
     */
    private String extractThinking(HrAgentState state) {
        List<String> toolResults = state.<List<String>>value(HrAgentState.TOOL_RESULTS_KEY).orElse(List.<String>of());
        if (toolResults == null || toolResults.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int step = 1;
        for (String result : toolResults) {
            if (result != null && !result.isBlank()) {
                sb.append("步骤 ").append(step++).append("：").append(result).append("\n\n");
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 提取工具调用步骤记录
     */
    private List<ToolCallStep> extractToolSteps(HrAgentState state) {
        List<ToolCallRecord> calls = state.toolCalls();
        if (calls == null || calls.isEmpty()) {
            return null;
        }
        List<ToolCallStep> steps = new ArrayList<>();
        String now = LocalDateTime.now().format(DTF);
        for (ToolCallRecord record : calls) {
            if (record != null) {
                steps.add(new ToolCallStep(
                    record.name() != null ? record.name() : "未知工具",
                    record.arguments() != null ? record.arguments() : "{}",
                    null,
                    now
                ));
            }
        }
        return steps.isEmpty() ? null : steps;
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
}