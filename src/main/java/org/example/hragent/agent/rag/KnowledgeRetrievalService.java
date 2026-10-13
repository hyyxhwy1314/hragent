package org.example.hragent.agent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.example.hragent.agent.rag.QdrantVectorClient.VectorHit;
import org.example.hragent.entity.KnowledgeChunk;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.utils.CurrentUserService.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 知识库检索服务（RAG 的检索层）。
 * <p>
 * 查询向量化 → Qdrant 相似度检索（带可见性预过滤）→ 返回带引用的原文片段。
 * 任何检索异常都降级为空结果，避免影响主流程。
 */
@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    /** 可访问 HR 专属知识的角色 */
    private static final Set<String> HR_ROLES = Set.of("HR", "HRBP", "ADMIN");

    private final EmbeddingModel embeddingModel;
    private final QdrantVectorClient vectorClient;
    private final RagProperties props;

    public KnowledgeRetrievalService(EmbeddingModel embeddingModel,
                                     QdrantVectorClient vectorClient,
                                     RagProperties props) {
        this.embeddingModel = embeddingModel;
        this.vectorClient = vectorClient;
        this.props = props;
    }

    /** 检索命中的知识片段（携带引用所需的元数据） */
    public record RetrievedChunk(String docTitle, String category, double score, String content) {
    }

    /**
     * 检索与查询最相关的知识片段。
     *
     * @param query 用户问题
     * @return 按相关度降序的片段列表；未启用/无命中/异常时返回空列表
     */
    public List<RetrievedChunk> retrieve(String query) {
        if (!props.isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            Embedding embedding = embeddingModel.embed(query).content();
            List<VectorHit> hits = vectorClient.search(
                    embedding.vector(), props.getTopK(), props.getMinScore(), allowedVisibilities());
            return hits.stream().map(this::toChunk).toList();
        } catch (Exception e) {
            log.warn("知识库检索失败，降级返回空结果：{}", e.getMessage());
            return List.of();
        }
    }

    /** 权限预过滤：HR 角色可见 HR 专属知识，其余仅见全员可见知识 */
    private Collection<String> allowedVisibilities() {
        CurrentUser user = CurrentUserService.get();
        String role = user == null ? null : user.getRole();
        return HR_ROLES.contains(role)
                ? List.of(KnowledgeChunk.VISIBILITY_PUBLIC, KnowledgeChunk.VISIBILITY_HR)
                : List.of(KnowledgeChunk.VISIBILITY_PUBLIC);
    }

    private RetrievedChunk toChunk(VectorHit hit) {
        Map<String, Object> payload = hit.payload();
        return new RetrievedChunk(
                text(payload.get("docTitle")),
                text(payload.get("category")),
                hit.score(),
                text(payload.get("content")));
    }

    private String text(Object value) {
        return Objects.toString(value, "");
    }
}
