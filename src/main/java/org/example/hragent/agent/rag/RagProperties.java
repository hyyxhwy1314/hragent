package org.example.hragent.agent.rag;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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

    /** 单次检索召回的片段数 */
    private int topK = 5;

    /** 相似度阈值，低于该值的片段不返回 */
    private double minScore = 0.35;

    /** 文档切分片段大小（字符） */
    private int chunkSize = 400;

    /** 相邻片段重叠字符数 */
    private int chunkOverlap = 80;

    /** 启动时是否重建知识库（删除旧片段与旧集合后重新向量化） */
    private boolean reindex = false;

    private Qdrant qdrant = new Qdrant();

    @Data
    public static class Qdrant {
        private String host = "127.0.0.1";
        private int port = 6333;
        private String collection = "hr_knowledge";
        /** 向量维度，需与 embedding 模型输出一致（text-embedding-v3 默认 1024） */
        private int vectorDim = 1024;
    }
}
