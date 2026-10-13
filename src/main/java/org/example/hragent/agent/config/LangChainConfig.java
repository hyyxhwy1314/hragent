package org.example.hragent.agent.config;

import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
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

    @Value("${ai.model.name:qwen3.7-flash}")
    private String modelName;

    @Value("${ai.embedding.model.name:text-embedding-v3}")
    private String embeddingModelName;

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

    /**
     * Tokenizer：用于会话记忆按 token 预算截断（TokenWindowChatMemory）。
     * 使用 OpenAI cl100k 编码近似估算 token 数（兼容 OpenAI 接口的模型均可近似使用）
     */
    @Bean
    public Tokenizer tokenizer() {
        return new OpenAiTokenizer();
    }

    /**
     * 向量化模型（Embedding）：与对话模型共用同一 OpenAI 兼容端点，仅模型名不同。
     * 用于知识库文档入库向量化与检索时的查询向量化。
     */
    @Bean
    public EmbeddingModel qwenEmbeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(2)
                .build();
    }
}