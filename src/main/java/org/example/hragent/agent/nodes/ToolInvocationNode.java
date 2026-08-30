package org.example.hragent.agent.nodes;

import org.bsc.langgraph4j.action.NodeAction;
import org.example.hragent.agent.state.HrAgentState;
import org.example.hragent.agent.tools.HrAgentAiServiceFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用节点（核心节点）
 * <p>
 * 完全基于 LangChain4j 的 AiService + ChatMemory：
 * <ol>
 *     <li>从状态中获取 chatMemoryId，定位会话专属的 ChatMemory</li>
 *     <li>通过 AiService.chat(memoryId, query) 调用大模型</li>
 *     <li>LangChain4j 内部自动完成：对话历史管理 → LLM 推理 → @Tool 工具调用 → LLM 生成最终回答</li>
 *     <li>将用户消息和 AI 回答写入 MESSAGES_KEY，供后续持久化</li>
 * </ol>
 */
@Component
public class ToolInvocationNode implements NodeAction<HrAgentState> {

    private final HrAgentAiServiceFactory aiServiceFactory;

    public ToolInvocationNode(HrAgentAiServiceFactory aiServiceFactory) {
        this.aiServiceFactory = aiServiceFactory;
    }

    @Override
    public Map<String, Object> apply(HrAgentState state) {
        try {
            // 1. 获取会话的 ChatMemory ID
            Long chatMemoryId = state.chatMemoryId();
            if (chatMemoryId == null) {
                // 降级：用 sessionId 的 hashCode 作为 memoryId
                String sessionId = state.sessionId();
                chatMemoryId = sessionId != null ? (long) sessionId.hashCode() : 0L;
            }

            // 2. 从消息列表中提取最后一条用户消息
            String userQuery = extractLastUserMessage(state);
            if (userQuery.isEmpty()) {
                return handleError("未找到用户消息");
            }

            // 3. 通过 LangChain4j AiService 调用（核心）
            //    AiService 内部自动：
            //    a) 从 ChatMemory 加载该会话的对话历史
            //    b) 将 [系统提示词 + 历史消息 + 用户消息] 发给 LLM
            //    c) LLM 决定是否调用 @Tool 方法
            //    d) 如果调用了工具，将工具结果再发给 LLM 生成最终回答
            //    e) 将完整的 user/assistant 消息存入 ChatMemory
            //    f) 返回最终文本回答
            var aiService = aiServiceFactory.getOrCreate(chatMemoryId);
            String aiResponse = aiService.chat(chatMemoryId, userQuery);

            // 4. 将用户消息和 AI 回答都写入 MESSAGES_KEY（用于持久化到 MySQL）
            //    MESSAGES_KEY 是 appender 通道，需以 List 形式一次性传入；
            //    不能对同一 key 调用两次 put（HashMap 会覆盖，导致用户消息丢失）
            List<String> conversationMessages = new ArrayList<>();
            conversationMessages.add(HrAgentState.USER_PREFIX + userQuery);
            conversationMessages.add(HrAgentState.ASSISTANT_PREFIX + aiResponse);
            Map<String, Object> updates = new HashMap<>();
            updates.put(HrAgentState.MESSAGES_KEY, conversationMessages);
            updates.put(HrAgentState.TOOL_RESULTS_KEY, aiResponse);

            return updates;

        } catch (Exception e) {
            return handleError("工具执行失败：" + e.getMessage());
        }
    }

    /**
     * 从消息列表中提取最后一条用户消息
     */
    private String extractLastUserMessage(HrAgentState state) {
        List<String> messages = state.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            String msg = messages.get(i);
            if (msg.startsWith(HrAgentState.USER_PREFIX)) {
                return msg.substring(HrAgentState.USER_PREFIX.length());
            }
        }
        return "";
    }

    private Map<String, Object> handleError(String errorMessage) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(HrAgentState.ERROR_MESSAGE_KEY, errorMessage);
        updates.put(HrAgentState.TOOL_RESULTS_KEY, "错误：" + errorMessage);
        return updates;
    }
}
