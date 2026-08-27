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
 */
@Configuration
public class LangChainConfig {

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.model.name:qwen3.7-plus-2026-05-26}")
    private String modelName;

    /**
     * 配置 AI 对话模型（阿里云 MaaS 兼容模式）
     */
    @Bean
    public ChatLanguageModel qwenChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                .build();
    }
}