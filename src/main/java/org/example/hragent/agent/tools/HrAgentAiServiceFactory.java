package org.example.hragent.agent.tools;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * HR Agent AI 服务工厂
 * <p>
 * 为每个会话创建独立的 AiService 实例，各自拥有独立的 ChatMemory，
 * 实现真正的多轮对话上下文管理。核心完全基于 LangChain4j：
 * <ul>
 *     <li>ChatMemory — 对话记忆管理</li>
 *     <li>AiServices — 绑定 @Tool + ChatMemory + ChatLanguageModel</li>
 *     <li>@Tool — 业务工具自动发现与调用</li>
 * </ul>
 */
@Component
public class HrAgentAiServiceFactory {

    private final ChatLanguageModel chatModel;
    private final HrBusinessTools hrBusinessTools;

    /** 每个会话独立的 ChatMemory，key = sessionId */
    private final ConcurrentHashMap<Long, ChatMemory> chatMemoryMap = new ConcurrentHashMap<>();

    /** 每个会话对应的 AiService 实例，key = sessionId */
    private final ConcurrentHashMap<Long, HrAgentAiService> aiServiceMap = new ConcurrentHashMap<>();

    /** 默认保留的最近消息轮数（20 条 ≈ 10 轮对话） */
    private static final int MAX_MESSAGES = 20;

    public HrAgentAiServiceFactory(ChatLanguageModel chatModel,
                                    HrBusinessTools hrBusinessTools) {
        this.chatModel = chatModel;
        this.hrBusinessTools = hrBusinessTools;
    }

    /**
     * 获取指定会话的 AiService（懒创建，自带独立 ChatMemory）
     *
     * @param sessionId 会话 ID（Long 类型，LangChain4j ChatMemory 要求）
     * @return 绑定了该会话 ChatMemory 的 AiService 实例
     */
    public HrAgentAiService getOrCreate(Long sessionId) {
        return aiServiceMap.computeIfAbsent(sessionId, id ->
                AiServices.builder(HrAgentAiService.class)
                        .chatLanguageModel(chatModel)
                        .tools(hrBusinessTools)
                        .chatMemoryProvider(memoryId -> getOrCreateChatMemory(memoryId))
                        .build()
        );
    }

    /**
     * 获取或创建指定会话的 ChatMemory
     * 注意：langchain4j 0.35.0 的 ChatMemoryProvider.get(Object memoryId) 参数为 Object
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
     * 获取指定会话 ChatMemory 中的消息列表（用于持久化到 MySQL）
     */
    public java.util.List<dev.langchain4j.data.message.ChatMessage> getMessages(Long sessionId) {
        ChatMemory memory = chatMemoryMap.get(sessionId);
        if (memory == null) {
            return java.util.List.of();
        }
        return memory.messages();
    }

    /**
     * 清除指定会话的 ChatMemory 和 AiService（会话结束时调用）
     */
    public void clearSession(Long sessionId) {
        ChatMemory memory = chatMemoryMap.remove(sessionId);
        if (memory != null) {
            memory.clear();
        }
        aiServiceMap.remove(sessionId);
    }
}
