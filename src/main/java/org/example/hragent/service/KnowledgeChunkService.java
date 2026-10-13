package org.example.hragent.service;

import org.example.hragent.entity.KnowledgeChunk;

/**
 * 知识库文档片段 Service。
 * 原文与元数据存 MySQL；向量侧由 {@code QdrantVectorClient} 承载。
 */
public interface KnowledgeChunkService extends BaseService<KnowledgeChunk> {

    /** 物理清空全部片段（重建索引前调用） */
    void truncateAll();
}
