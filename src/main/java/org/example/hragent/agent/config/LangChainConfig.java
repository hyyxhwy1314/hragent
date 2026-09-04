package org.example.hragent.agent.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 配置类
 * 为 HR Agent 配置大语言模型等 AI 能力
 * 使用阿里云 MaaS 兼容模式（OpenAI 兼容接口）
 * <p>
 * AiService 实例由 HrAgentAiServiceFactory 按会话动态创建，
 * 每个会话拥有独立的 ChatMemory，此处只配置底层模型。
 */
@Configuration
public class LangChainConfig {

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.model.name:deepseek-v4-flash-0731}")
    private String modelName;

    /**
     * 配置 AI 对话模型（阿里云 MaaS 兼容模式）
     * 这是 LangChain4j 的核心 Bean，所有 AiService 都基于此模型
     */
    @Bean
    public ChatLanguageModel qwenChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(120))
                .maxRetries(2)
                .build();
    }
}