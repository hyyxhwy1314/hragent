package org.example.hragent.agent.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识库检索（RAG）配置。
 * <p>
 * 原文与元数据存 MySQL，向量存 Qdrant；本配置同时约束切分参数与检索参数。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "hragent.rag")
public class RagProperties {

    /** 是否启用知识库检索（关闭时 hrKnowledgeQA 退回模板话术） */
    private boolean enabled = true;

    /** 单次检索最终返回的片段数（精排后） */
    private int topK = 5;

    /** 粗排召回条数（向量检索），随后交给 rerank 精排 */
    private int recallTopK = 20;

    /** 粗排相似度阈值，低于该值的片段不进入精排 */
    private double minScore = 0.3;

    /** 结构感知切分的片段大小（字符） */
    private int chunkSize = 400;

    /** 长段落按句切分时的相邻片段重叠字符数 */
    private int chunkOverlap = 80;

    /** 父子块回填时父块正文的最大字符数，超出截断，避免单条命中占满上下文 */
    private int parentMaxChars = 1200;

    /** 启动时是否重建知识库（删除旧片段与旧集合后重新向量化） */
    private boolean reindex = false;

    /** 文档时效治理（有效期、复审、过期失效） */
    private Freshness freshness = new Freshness();

    /** 精排（rerank）：两阶段检索的第二阶段 */
    private Rerank rerank = new Rerank();

    private Qdrant qdrant = new Qdrant();

    /**
     * 文档时效治理配置。
     * <p>
     * 过期不删除：文档到期后仅置为 EXPIRED 并从检索中硬过滤，保留原文用于审计。
     */
    @Data
    public static class Freshness {
        /** 是否启用时效治理（关闭时检索不再按有效期过滤，仅用于排障） */
        private boolean enabled = true;

        /** 默认复审周期（天），未在文档元信息中声明时按分类策略或该默认值计算 */
        private int defaultReviewDays = 365;

        /** 复审到期宽限期（天）：超过复审日仍未复审，宽限期结束后自动失效 */
        private int graceDays = 30;

        /** 复审提醒提前天数（到期前 N 天开始提醒 HR 复审） */
        private int reminderDays = 30;

        /** 失效提示阈值：失效片段精排得分达到该值且高于现行片段时，才提示用户「相关制度已失效」 */
        private double hintMinScore = 0.5;

        /** 分类复审周期（天），键为文档元信息中的「分类」 */
        private Map<String, Integer> categoryReviewDays = new LinkedHashMap<>();
    }

    @Data
    public static class Qdrant {
        private String host = "127.0.0.1";
        private int port = 6333;
        private String collection = "hr_knowledge";
        /** 向量维度，需与 embedding 模型输出一致（qwen3.7-text-embedding-flash 默认 1024） */
        private int vectorDim = 1024;
    }

    /**
     * 精排配置：复用主模型厂商的访问地址（ai.base-url 的主机部分）与密钥，仅模型名不同。
     */
    @Data
    public static class Rerank {
        private boolean enabled = true;
        /** 精排模型名 */
        private String modelName = "qwen3.7-text-rerank";
        /** 精排接口路径（DashScope 原生 text-rerank） */
        private String path = "/api/v1/services/rerank/text-rerank/text-rerank";
        /** 精排接口密钥，默认复用 ai.api-key */
        private String apiKey = "";
        /** 精排请求超时（秒） */
        private int timeoutSeconds = 15;
    }
}
