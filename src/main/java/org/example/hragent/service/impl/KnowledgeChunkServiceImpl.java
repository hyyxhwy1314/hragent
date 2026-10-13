package org.example.hragent.service.impl;

import org.example.hragent.entity.KnowledgeChunk;
import org.example.hragent.mapper.KnowledgeChunkMapper;
import org.example.hragent.service.KnowledgeChunkService;
import org.springframework.stereotype.Service;

/**
 * 知识库文档片段 Service 实现。
 * 写入仅发生在启动/重建索引阶段，检索热路径走 Qdrant，故不使用缓存。
 */
@Service
public class KnowledgeChunkServiceImpl extends BaseServiceImpl<KnowledgeChunkMapper, KnowledgeChunk>
        implements KnowledgeChunkService {

    @Override
    public void truncateAll() {
        baseMapper.truncateAll();
    }
}
