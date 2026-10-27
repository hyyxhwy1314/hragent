package org.example.hragent.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.example.hragent.entity.KnowledgeChunk;
import org.example.hragent.mapper.KnowledgeChunkMapper;
import org.example.hragent.service.KnowledgeChunkService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档片段 Service 实现。
 * <p>
 * 写入仅发生在启动/增量入库阶段，检索热路径走 Qdrant，故不使用缓存。
 * 文档采用版本化治理：同一 docCode 只保留一个 ACTIVE 版本，旧版本 SUPERSEDED。
 */
@Service
public class KnowledgeChunkServiceImpl extends BaseServiceImpl<KnowledgeChunkMapper, KnowledgeChunk>
        implements KnowledgeChunkService {

    @Override
    public void truncateAll() {
        baseMapper.truncateAll();
    }

    @Override
    public List<DocState> latestDocStates() {
        List<KnowledgeChunk> all = list();
        Map<String, KnowledgeChunk> latest = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : all) {
            latest.merge(chunk.getDocCode(), chunk,
                    (current, candidate) -> versionOf(candidate) > versionOf(current) ? candidate : current);
        }
        return latest.values().stream()
                .map(chunk -> new DocState(
                        chunk.getDocCode(),
                        chunk.getDocTitle(),
                        versionOf(chunk),
                        chunk.getStatus(),
                        chunk.getSourceHash(),
                        chunk.getEffectiveTo(),
                        chunk.getReviewDueAt()))
                .toList();
    }

    @Override
    public void supersedeDoc(String docCode, int newVersion) {
        update(null, new LambdaUpdateWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocCode, docCode)
                .ne(KnowledgeChunk::getStatus, KnowledgeChunk.STATUS_SUPERSEDED)
                .set(KnowledgeChunk::getStatus, KnowledgeChunk.STATUS_SUPERSEDED)
                .set(KnowledgeChunk::getSupersededBy, newVersion));
    }

    @Override
    public void markStatus(String docCode, String status) {
        // 仅变更现行版本的状态，历史 SUPERSEDED 版本保留原状态用于审计
        update(null, new LambdaUpdateWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocCode, docCode)
                .eq(KnowledgeChunk::getStatus, KnowledgeChunk.STATUS_ACTIVE)
                .set(KnowledgeChunk::getStatus, status));
    }

    private int versionOf(KnowledgeChunk chunk) {
        return chunk.getVersion() == null ? 0 : chunk.getVersion();
    }
}
