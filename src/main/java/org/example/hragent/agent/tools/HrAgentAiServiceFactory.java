package org.example.hragent.agent.tools;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 会话记忆工厂
 * <p>
 * 为每个会话维护独立的 ChatMemory（LangChain4j 对话记忆）。因 Agent 已改为
 * LangGraph 状态机图（model/action 循环）手写调用模型与工具，本类不再构建 AiService，
 * 只负责 ChatMemory 的创建与生命周期管理：
 * <ul>
 *     <li>{@link #getChatMemory(Long)} — 获取/创建某会话的对话记忆</li>
 *     <li>{@link #clearSession(Long)} — 会话结束时清理对应记忆</li>
 * </ul>
 */
@Component
public class HrAgentAiServiceFactory {

    /** 每个会话独立的 ChatMemory，key = 会话维度的 Long ID */
    private final ConcurrentHashMap<Long, ChatMemory> chatMemoryMap = new ConcurrentHashMap<>();

    /** 默认保留的最近消息条数（消息轮数一般 = 条数/2） */
    private static final int MAX_MESSAGES = 40;

    public HrAgentAiServiceFactory() {
    }

    /**
     * 获取指定会话的 ChatMemory（不存在则懒创建）
     *
     * @param sessionId 会话 ID（Long 类型）
     * @return 会话独立的 ChatMemory
     */
    public ChatMemory getChatMemory(Long sessionId) {
        return getOrCreateChatMemory(sessionId);
    }

    /**
     * 获取或创建指定会话的 ChatMemory
     */
    private ChatMemory getOrCreateChatMemory(Object memoryId) {
        Long id = memoryId instanceof Long l ? l : (long) String.valueOf(memoryId).hashCode();
        return chatMemoryMap.computeIfAbsent(id, key ->
                MessageWindowChatMemory.builder()
                        .maxMessages(MAX_MESSAGES)
                        .build()
        );
    }

    /**
     * 清除指定会话的 ChatMemory（会话结束时调用）
     */
    public void clearSession(Long sessionId) {
        ChatMemory memory = chatMemoryMap.remove(sessionId);
        if (memory != null) {
            memory.clear();
        }
    }
}
