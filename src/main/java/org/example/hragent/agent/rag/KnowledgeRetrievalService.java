package org.example.hragent.agent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.example.hragent.agent.rag.QdrantVectorClient.RetrievalFilter;
import org.example.hragent.agent.rag.QdrantVectorClient.VectorHit;
import org.example.hragent.entity.KnowledgeChunk;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.utils.CurrentUserService.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 知识库检索服务（RAG 的检索层）。
 * <p>
 * 两阶段检索（业界主流的 retrieve-then-rerank）：
 * <ol>
 *   <li><b>粗排</b>：扩大召回（recall-top-k），向量相似度取候选；</li>
 *   <li><b>精排</b>：候选交给 rerank 模型重新打分排序，取 top-k；精排失败自动回退粗排顺序；</li>
 *   <li><b>父子回填</b>：命中子块后按父块补全章节上下文，同章节去重。</li>
 * </ol>
 * 检索时对「可见性 + 文档状态 + 生效/失效/复审日期」做硬过滤：
 * 过期文档（EXPIRED）与被新版本替代的文档（SUPERSEDED）不会进入上下文，
 * 而是作为“已失效提示”单独返回，供上层解释“为什么没有现行条款”。
 * 任何检索异常都降级为空结果，避免影响主流程。
 */
@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    /** 可访问 HR 专属知识的角色 */
    private static final Set<String> HR_ROLES = Set.of("HR", "HRBP", "ADMIN");

    private final EmbeddingModel embeddingModel;
    private final QdrantVectorClient vectorClient;
    private final RerankClient rerankClient;
    private final RagProperties props;

    public KnowledgeRetrievalService(EmbeddingModel embeddingModel,
                                     QdrantVectorClient vectorClient,
                                     RerankClient rerankClient,
                                     RagProperties props) {
        this.embeddingModel = embeddingModel;
        this.vectorClient = vectorClient;
        this.rerankClient = rerankClient;
        this.props = props;
    }

    /** 检索命中的知识片段（携带引用、章节与时效所需的元数据） */
    public record RetrievedChunk(String docTitle,
                                 String sectionPath,
                                 String category,
                                 int version,
                                 String status,
                                 LocalDate effectiveFrom,
                                 LocalDate effectiveTo,
                                 double score,
                                 String content) {

        RetrievedChunk withScore(double value) {
            return new RetrievedChunk(docTitle, sectionPath, category, version, status,
                    effectiveFrom, effectiveTo, value, content);
        }

        RetrievedChunk withContent(String value) {
            return new RetrievedChunk(docTitle, sectionPath, category, version, status,
                    effectiveFrom, effectiveTo, score, value);
        }
    }

    /**
     * 检索结果：现行有效片段 + 已失效片段。
     * <p>
     * 仅当现行片段为空时才返回失效片段，用于向用户说明“该制度已过期/被替代”，不可作为答复依据。
     */
    public record RetrievalResult(List<RetrievedChunk> chunks, List<RetrievedChunk> expiredChunks) {

        public static RetrievalResult empty() {
            return new RetrievalResult(List.of(), List.of());
        }
    }

    /** 失效提示最多返回的片段数 */
    private static final int HINT_LIMIT = 2;

    /**
     * 检索与查询最相关的现行有效知识片段。
     * <p>
     * 现行与失效片段一次性召回、统一精排（同一批次得分可比），再分别处理：
     * 现行片段作为答复依据；当失效片段得分明显更高（或根本没有现行条款）时，
     * 额外给出「相关制度已失效」提示，避免用无关条款搪塞用户。
     *
     * @param query 用户问题
     * @return 现行片段与失效提示；未启用/无命中/异常时返回空结果
     */
    public RetrievalResult retrieve(String query) {
        if (!props.isEnabled() || query == null || query.isBlank()) {
            return RetrievalResult.empty();
        }
        try {
            Embedding embedding = embeddingModel.embed(query).content();
            LocalDate today = LocalDate.now();
            boolean enforce = props.getFreshness().isEnabled();

            List<Candidate> candidates = new ArrayList<>(recall(embedding, new RetrievalFilter(
                    allowedVisibilities(),
                    enforce ? List.of(KnowledgeChunk.STATUS_ACTIVE) : List.of(),
                    enforce, today, props.getFreshness().getGraceDays())));
            if (enforce) {
                // 失效/被替代版本仅用于提示，不校验有效期（它们本就已过期）
                candidates.addAll(recall(embedding, new RetrievalFilter(
                        allowedVisibilities(),
                        List.of(KnowledgeChunk.STATUS_EXPIRED, KnowledgeChunk.STATUS_SUPERSEDED),
                        false, today, 0)));
            }

            List<Candidate> ordered = rerank(query, candidates);
            List<Candidate> activeOrdered = enforce
                    ? ordered.stream().filter(c -> KnowledgeChunk.STATUS_ACTIVE.equals(c.chunk().status())).toList()
                    : ordered;
            List<Candidate> expiredOrdered = enforce
                    ? ordered.stream().filter(c -> !KnowledgeChunk.STATUS_ACTIVE.equals(c.chunk().status())).toList()
                    : List.of();

            List<RetrievedChunk> chunks = toChunks(activeOrdered, props.getTopK());
            List<RetrievedChunk> hints = hints(chunks, expiredOrdered);
            log.debug("知识库检索：召回 {} 条（现行 {} / 失效 {}），现行返回 {} 条、失效提示 {} 条",
                    candidates.size(), activeOrdered.size(), expiredOrdered.size(), chunks.size(), hints.size());
            // 更贴题的条款已失效时不再拿弱相关现行条款凑答案，交由上层提示用户确认现行规定
            return hints.isEmpty()
                    ? new RetrievalResult(chunks, List.of())
                    : new RetrievalResult(List.of(), hints);
        } catch (Exception e) {
            log.warn("知识库检索失败，降级返回空结果：{}", e.getMessage());
            return RetrievalResult.empty();
        }
    }

    /**
     * 失效提示：失效片段精排得分达到阈值，且高于现行片段最高分（或没有现行片段）时才返回，
     * 否则说明现行制度已足以回答，无需提示历史版本。
     */
    private List<RetrievedChunk> hints(List<RetrievedChunk> chunks, List<Candidate> expiredOrdered) {
        if (expiredOrdered.isEmpty()) {
            return List.of();
        }
        double bestExpired = expiredOrdered.get(0).chunk().score();
        boolean aboveFloor = bestExpired >= props.getFreshness().getHintMinScore();
        boolean beatsActive = chunks.isEmpty() || bestExpired > chunks.get(0).score();
        return aboveFloor && beatsActive ? toChunks(expiredOrdered, HINT_LIMIT) : List.of();
    }

    /** 同章节多个子块命中时只保留得分最高的一条，避免父块重复占满上下文 */
    private List<RetrievedChunk> toChunks(List<Candidate> ordered, int limit) {
        Map<String, Candidate> deduped = new LinkedHashMap<>();
        for (Candidate candidate : ordered) {
            deduped.putIfAbsent(candidate.parentKey(), candidate);
        }
        return deduped.values().stream().limit(limit).map(this::expandParent).toList();
    }

    private List<Candidate> recall(Embedding embedding, RetrievalFilter filter) {
        List<VectorHit> hits = vectorClient.search(
                embedding.vector(), props.getRecallTopK(), props.getMinScore(), filter);
        return hits.stream().map(this::toCandidate).toList();
    }

    /** 精排失败（返回空）时保持向量粗排顺序 */
    private List<Candidate> rerank(String query, List<Candidate> candidates) {
        if (!props.getRerank().isEnabled() || candidates.size() <= 1) {
            return candidates;
        }
        List<String> documents = candidates.stream().map(c -> c.chunk().sectionPath().isBlank()
                ? c.chunk().content()
                : c.chunk().sectionPath() + "\n" + c.chunk().content()).toList();
        List<RerankClient.Ranked> ranked = rerankClient.rerank(query, documents, documents.size());
        if (ranked.isEmpty()) {
            return candidates;
        }
        List<Candidate> ordered = new ArrayList<>();
        for (RerankClient.Ranked item : ranked) {
            if (item.index() < candidates.size()) {
                ordered.add(candidates.get(item.index()).withScore(item.score()));
            }
        }
        return ordered.isEmpty() ? candidates : ordered;
    }

    /** 父块回填：子块只用于命中，回答上下文用整节正文；父块过大时保留子块，避免截断丢信息 */
    private RetrievedChunk expandParent(Candidate candidate) {
        if (!candidate.expandable()) {
            return candidate.chunk();
        }
        String parent = candidate.parentContent();
        int max = props.getParentMaxChars();
        String content = parent.length() <= max ? parent : parent.substring(0, max).trim() + "……";
        return candidate.chunk().withContent(content);
    }

    /** 权限预过滤：HR 角色可见 HR 专属知识，其余仅见全员可见知识 */
    private Collection<String> allowedVisibilities() {
        CurrentUser user = CurrentUserService.get();
        String role = user == null ? null : user.getRole();
        return HR_ROLES.contains(role)
                ? List.of(KnowledgeChunk.VISIBILITY_PUBLIC, KnowledgeChunk.VISIBILITY_HR)
                : List.of(KnowledgeChunk.VISIBILITY_PUBLIC);
    }

    private Candidate toCandidate(VectorHit hit) {
        Map<String, Object> payload = hit.payload();
        RetrievedChunk chunk = new RetrievedChunk(
                text(payload.get("docTitle")),
                text(payload.get("sectionPath")),
                text(payload.get("category")),
                intValue(payload.get("version")),
                text(payload.get("status")),
                dateValue(payload.get("effectiveFrom")),
                dateValue(payload.get("effectiveTo")),
                hit.score(),
                text(payload.get("content")));
        String parent = text(payload.get("parentContent"));
        // 父块过长时不做回填（截断会丢失命中内容），此时按子块返回并允许同章节多条命中
        boolean expandable = !parent.isBlank() && parent.length() <= props.getParentMaxChars();
        return new Candidate(chunk, text(payload.get("parentId")), parent, expandable);
    }

    private String text(Object value) {
        return Objects.toString(value, "");
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private LocalDate dateValue(Object value) {
        String text = text(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    /** 候选片段：公开结果 + 父子块信息（父块不对外暴露结构，只用于上下文回填） */
    private record Candidate(RetrievedChunk chunk, String parentId, String parentContent, boolean expandable) {

        private String parentKey() {
            if (!expandable) {
                return chunk.docTitle() + "#" + chunk.content();
            }
            return parentId.isBlank() ? chunk.docTitle() + "#" + chunk.sectionPath() : parentId;
        }

        private Candidate withScore(double score) {
            return new Candidate(chunk.withScore(score), parentId, parentContent, expandable);
        }
    }
}
