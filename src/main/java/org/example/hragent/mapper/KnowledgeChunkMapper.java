package org.example.hragent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.example.hragent.entity.KnowledgeChunk;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    /** 物理清空知识库片段（重建索引时使用，绕过逻辑删除） */
    @Delete("DELETE FROM t_knowledge_chunk")
    void truncateAll();
}
