package org.example.hragent.service;

import org.example.hragent.entity.KnowledgeChunk;

import java.time.LocalDate;
import java.util.List;

/**
 * 知识库文档片段 Service。
 * 原文与元数据存 MySQL；向量侧由 {@code QdrantVectorClient} 承载。
 */
public interface KnowledgeChunkService extends BaseService<KnowledgeChunk> {

    /** 物理清空全部片段（重建索引前调用） */
    void truncateAll();

    /**
     * 文档级最新版本状态（每个 docCode 仅保留版本号最大的片段汇总）。
     * <p>
     * 用于增量入库变更检测与时效巡检。
     */
    List<DocState> latestDocStates();

    /** 将文档已有版本标记为 SUPERSEDED（新版本入库前调用） */
    void supersedeDoc(String docCode, int newVersion);

    /** 更新文档全部片段的状态（时效巡检置 EXPIRED 使用） */
    void markStatus(String docCode, String status);

    /**
     * 文档级状态快照。
     */
    record DocState(String docCode,
                    String docTitle,
                    int version,
                    String status,
                    String sourceHash,
                    LocalDate effectiveTo,
                    LocalDate reviewDueAt) {
    }
}
