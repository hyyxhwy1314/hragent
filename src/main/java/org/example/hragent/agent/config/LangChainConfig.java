package org.example.hragent.agent.config;

import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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

    @Value("${ai.model.name:qwen3.8-max}")
    private String modelName;

    @Value("${ai.embedding.model.name:qwen3.7-text-embedding-flash}")
    private String embeddingModelName;

    @Value("${ai.routing.base-url:http://127.0.0.1:11434/v1}")
    private String routingBaseUrl;

    @Value("${ai.routing.model-name:qwen2.5:3b-instruct}")
    private String routingModelName;

    @Value("${ai.routing.api-key:ollama}")
    private String routingApiKey;

    @Value("${ai.routing.timeout-seconds:20}")
    private long routingTimeoutSeconds;

    @Value("${ai.routing.llm-timeout-seconds:300}")
    private long localLlmTimeoutSeconds;

    /**
     * 远程 API 对话模型（阿里云 MaaS 兼容模式）。
     * 保留本 Bean 与其凭据配置不变；是否作为注入首选由下方主模型选择器按开关决定，
     * 开启全量本地化后本 Bean 仍存在，可随时切回。
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
     * 本地轻量决策模型（本机 Ollama 的 OpenAI 兼容端点，程序与模型目录部署在 D 盘）。
     * 仅当 ai.routing.enabled=true 时装配，且只服务于工具路由的业务域分类决策，
     * 不参与最终作答，因此模型能力不足时影响面最小、可一键回退。
     */
    @Bean
    @ConditionalOnProperty(name = "ai.routing.enabled", havingValue = "true")
    public ChatLanguageModel routingChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(routingApiKey)
                .baseUrl(routingBaseUrl)
                .modelName(routingModelName)
                .timeout(Duration.ofSeconds(routingTimeoutSeconds))
                .maxRetries(0)
                .build();
    }

    /**
     * 全量本地化时的本地主模型：与 routingChatModel 共用同一本地端点，但超时更宽松。
     * 路由决策调用需要快速失败并回退（短超时），而最终作答/工具参数生成需要给足时间
     * （本地 CPU 推理较慢），故独立配置超时。
     */
    @Bean
    @ConditionalOnProperty(name = "ai.routing.local-primary", havingValue = "true")
    public ChatLanguageModel localAgentChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(routingApiKey)
                .baseUrl(routingBaseUrl)
                .modelName(routingModelName)
                .timeout(Duration.ofSeconds(localLlmTimeoutSeconds))
                .maxRetries(0)
                .build();
    }

    /**
     * 主模型选择器（默认）：ai.routing.local-primary 非 true 时，注入首选主模型仍为远程 API 模型，
     * 行为与以往完全一致。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "ai.routing.local-primary", havingValue = "false", matchIfMissing = true)
    public ChatLanguageModel remotePrimaryChatModel(@Qualifier("qwenChatModel") ChatLanguageModel apiModel) {
        return apiModel;
    }

    /**
     * 主模型选择器（全量本地化）：ai.routing.local-primary=true 时，注入首选主模型切为本地模型，
     * 使 Agent 最终作答与工具参数生成、简历深度分析、合同审查等所有按类型注入主模型的能力
     * 全部改由本地模型完成。通过装配层互斥条件切换，无运行时条件分支，关闭开关即一键切回远程。
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "ai.routing.local-primary", havingValue = "true")
    public ChatLanguageModel localPrimaryChatModel(@Qualifier("localAgentChatModel") ChatLanguageModel localModel) {
        return localModel;
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