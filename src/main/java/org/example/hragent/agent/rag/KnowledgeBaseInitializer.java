package org.example.hragent.agent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.example.hragent.agent.rag.QdrantVectorClient.VectorPoint;
import org.example.hragent.entity.KnowledgeChunk;
import org.example.hragent.service.KnowledgeChunkService;
import org.example.hragent.service.KnowledgeChunkService.DocState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库初始化与增量入库。
 * <p>
 * 治理策略（主流做法）：
 * <ul>
 *   <li><b>版本化</b>：同一文档内容变更时版本号 +1，旧版本置 SUPERSEDED 保留审计，
 *       旧向量保留并同步状态，仅新版本有效向量参与检索；</li>
 *   <li><b>增量</b>：按文档内容 SHA-256 比对，未变更文档跳过，避免每次启动全量向量化；</li>
 *   <li><b>时效</b>：文档携带生效/失效/复审日期，过期文档置 EXPIRED 且不入向量库；
 *       即使入库后在库中到期，检索层也会按日期硬过滤（见 {@code QdrantVectorClient}）；</li>
 *   <li><b>下线</b>：源文件被移除的文档置 EXPIRED 并清理向量。</li>
 * </ul>
 * 初始化失败只记录日志，不影响应用启动与其它功能。
 */
@Component
@Order(10)
public class KnowledgeBaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseInitializer.class);

    /** 内置知识文档目录（classpath） */
    private static final String DOC_ROOT = "knowledge";

    /** 单次向量化请求的片段数（受服务端批量上限约束） */
    private static final int EMBED_BATCH = 8;

    /** 未声明有效期时的归一上限（9999-12-31），供 Qdrant 数值范围过滤使用 */
    private static final long MAX_EPOCH_DAY = LocalDate.of(9999, 12, 31).toEpochDay();
    /** 未声明生效日时的归一下限 */
    private static final long MIN_EPOCH_DAY = LocalDate.of(1970, 1, 1).toEpochDay();

    private final KnowledgeChunkService chunkService;
    private final QdrantVectorClient vectorClient;
    private final EmbeddingModel embeddingModel;
    private final RagProperties props;

    public KnowledgeBaseInitializer(KnowledgeChunkService chunkService,
                                    QdrantVectorClient vectorClient,
                                    EmbeddingModel embeddingModel,
                                    RagProperties props) {
        this.chunkService = chunkService;
        this.vectorClient = vectorClient;
        this.embeddingModel = embeddingModel;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        if (!props.isEnabled()) {
            log.info("知识库检索已关闭，跳过初始化");
            return;
        }
        ingest();
    }

    public void ingest() {
        try {
            boolean collectionExisted = vectorClient.collectionExists();
            if (props.isReindex() || !collectionExisted) {
                // 重建或向量库丢失：MySQL 中的旧片段无法再被检索，直接清空后全量入库
                log.info("知识库全量重建（reindex={}, collectionExisted={}）",
                        props.isReindex(), collectionExisted);
                chunkService.truncateAll();
                if (props.isReindex()) {
                    vectorClient.recreateCollection();
                } else {
                    vectorClient.ensureCollection();
                }
            } else {
                vectorClient.ensureCollection();
            }

            Map<String, DocState> states = chunkService.latestDocStates().stream()
                    .collect(Collectors.toMap(DocState::docCode, state -> state, (a, b) -> a, LinkedHashMap::new));

            LocalDate today = LocalDate.now();
            int created = 0;
            int updated = 0;
            int skipped = 0;
            int expired = 0;
            int draft = 0;
            int totalChunks = 0;

            for (Resource resource : docResources()) {
                SourceDoc doc = loadDoc(resource);
                if (doc == null) {
                    continue;
                }
                DocState state = states.remove(doc.docCode());
                boolean changed = state == null
                        || state.sourceHash() == null
                        || !state.sourceHash().equals(doc.hash());

                int version = state == null ? doc.meta().version()
                        : Math.max(state.version() + 1, doc.meta().version());
                LocalDate reviewDueAt = resolveReviewDueAt(doc.meta(), today);
                String status = resolveStatus(doc.meta(), reviewDueAt, today);

                if (!changed) {
                    skipped++;
                    continue;
                }

                List<Prepared> chunks = buildChunks(doc, version, reviewDueAt, status);

                if (state != null) {
                    chunkService.supersedeDoc(doc.docCode(), version);
                    // 旧版本向量不物理删除：新版仍有效则置 SUPERSEDED，新版已失效则整体置 EXPIRED，
                    // 从而保留“废止条款提示”与审计回溯能力；新版本向量另行写入
                    vectorClient.updateStatusByDocCode(doc.docCode(),
                            KnowledgeChunk.STATUS_ACTIVE.equals(status)
                                    ? KnowledgeChunk.STATUS_SUPERSEDED : KnowledgeChunk.STATUS_EXPIRED);
                }
                if (KnowledgeChunk.STATUS_DRAFT.equals(status)) {
                    // 草稿从未正式发布，不进入向量库，避免被检索到
                    draft++;
                    log.warn("文档《{}》为草稿，不写入向量库：{}", doc.title(), doc.docCode());
                } else {
                    // 已失效文档仍写入向量库并标记 EXPIRED：现行检索会硬过滤掉，
                    // 但可被“已失效提示”检索命中，用于解释现行条款为何缺失
                    if (KnowledgeChunk.STATUS_EXPIRED.equals(status)) {
                        expired++;
                    }
                    vectorClient.upsert(embedAll(chunks));
                }
                chunkService.saveBatch(chunks.stream().map(Prepared::row).toList());
                totalChunks += chunks.size();
                if (state == null) {
                    created++;
                } else {
                    updated++;
                }
                log.info("知识库文档入库：{} v{}，状态 {}，生效 {} 至 {}，复审 {}，片段 {} 个",
                        doc.title(), version, status, doc.meta().effectiveFrom(),
                        doc.meta().effectiveTo() == null ? "长期" : doc.meta().effectiveTo(),
                        reviewDueAt, chunks.size());
            }

            // 源文件已移除：下线文档并从向量库清理，保留 MySQL 原文用于审计
            for (DocState removed : states.values()) {
                chunkService.markStatus(removed.docCode(), KnowledgeChunk.STATUS_EXPIRED);
                vectorClient.deleteByDocCode(removed.docCode());
                log.warn("知识库文档已下线（源文件不存在）：{}，历史片段置 EXPIRED", removed.docCode());
            }

            log.info("知识库同步完成：新增 {} 篇、更新 {} 篇、跳过 {} 篇、失效 {} 篇、草稿 {} 篇，本次写入 {} 个片段",
                    created, updated, skipped, expired, draft, totalChunks);
        } catch (Exception e) {
            log.error("知识库初始化失败（不影响其它功能）：{}", e.getMessage(), e);
        }
    }

    /** 扫描内置文档（classpath:knowledge/*.md），文件名排序保证入库顺序稳定 */
    private List<Resource> docResources() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:" + DOC_ROOT + "/*.md");
            return Arrays.stream(resources)
                    .sorted(Comparator.comparing(resource -> String.valueOf(resource.getFilename())))
                    .toList();
        } catch (IOException e) {
            log.warn("扫描知识文档失败：{}", e.getMessage());
            return List.of();
        }
    }

    /** 读取内置文档并解析元信息、正文与内容哈希 */
    private SourceDoc loadDoc(Resource resource) {
        String file = resource.getFilename();
        if (file == null) {
            return null;
        }
        String raw;
        try (InputStream in = resource.getInputStream()) {
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("读取知识文档失败：{} - {}", file, e.getMessage());
            return null;
        }
        List<String> lines = raw.lines().toList();
        String docCode = file.replace(".md", "");
        String title = titleOf(lines);
        DocMeta meta = metaOf(lines);
        return new SourceDoc(docCode, title, bodyOf(lines), sha256(raw), meta);
    }

    /** 按结构感知方式切分文档：标题分节 + 原子块 + 父子块 */
    private List<Prepared> buildChunks(SourceDoc doc, int version,
                                       LocalDate reviewDueAt, String status) {
        List<MarkdownChunker.Chunk> pieces = MarkdownChunker.split(
                doc.body(), props.getChunkSize(), props.getChunkOverlap());
        List<Prepared> prepared = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            MarkdownChunker.Chunk piece = pieces.get(i);
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocCode(doc.docCode());
            chunk.setDocTitle(doc.title());
            chunk.setCategory(doc.meta().category());
            chunk.setSeqNo(i);
            chunk.setContent(piece.content());
            chunk.setKeywords(doc.meta().keywords());
            chunk.setVisibility(doc.meta().visibility());
            chunk.setEmbeddingId(UUID.randomUUID().toString());
            chunk.setVersion(version);
            chunk.setStatus(status);
            chunk.setEffectiveFrom(doc.meta().effectiveFrom());
            chunk.setEffectiveTo(doc.meta().effectiveTo());
            chunk.setReviewDueAt(reviewDueAt);
            chunk.setSourceHash(doc.hash());
            prepared.add(new Prepared(chunk, piece));
        }
        return prepared;
    }

    /**
     * 计算复审截止日：文档元信息优先；否则按分类复审周期计算（首次入库时确定，之后随版本更新）。
     */
    private LocalDate resolveReviewDueAt(DocMeta meta, LocalDate today) {
        if (meta.reviewDueAt() != null) {
            return meta.reviewDueAt();
        }
        int days = props.getFreshness().getCategoryReviewDays()
                .getOrDefault(meta.category(), props.getFreshness().getDefaultReviewDays());
        return today.plusDays(days);
    }

    /**
     * 解析入库状态：到期时间优先于声明状态，避免出现“状态写着 ACTIVE 但已过期”的数据。
     */
    private String resolveStatus(DocMeta meta, LocalDate reviewDueAt, LocalDate today) {
        if (KnowledgeChunk.STATUS_DRAFT.equals(meta.status())
                || KnowledgeChunk.STATUS_EXPIRED.equals(meta.status())) {
            return meta.status();
        }
        if (meta.effectiveTo() != null && today.isAfter(meta.effectiveTo())) {
            return KnowledgeChunk.STATUS_EXPIRED;
        }
        if (reviewDueAt != null
                && today.isAfter(reviewDueAt.plusDays(props.getFreshness().getGraceDays()))) {
            return KnowledgeChunk.STATUS_EXPIRED;
        }
        return KnowledgeChunk.STATUS_ACTIVE;
    }

    /** 分批向量化片段，生成待写入 Qdrant 的点 */
    private List<VectorPoint> embedAll(List<Prepared> prepared) {
        List<VectorPoint> points = new ArrayList<>();
        for (int i = 0; i < prepared.size(); i += EMBED_BATCH) {
            List<Prepared> batch = prepared.subList(i, Math.min(prepared.size(), i + EMBED_BATCH));
            List<TextSegment> segments = batch.stream()
                    .map(item -> TextSegment.from(embedText(item)))
                    .toList();
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            for (int j = 0; j < batch.size(); j++) {
                Prepared item = batch.get(j);
                points.add(new VectorPoint(item.row().getEmbeddingId(),
                        toFloatList(embeddings.get(j).vector()), payload(item)));
            }
        }
        return points;
    }

    /**
     * 向量化文本：在子块前拼接章节路径，使同一个子块在脱离父块时仍具备上下文，
     * 避免「同一句话在不同章节含义不同」的召回歧义。
     */
    private String embedText(Prepared item) {
        String path = item.piece().sectionPath();
        return path.isBlank() ? item.row().getContent() : path + "\n" + item.row().getContent();
    }

    /**
     * 向量 payload：除引用元数据外，写入状态与归一为 epochDay 的有效期/复审日期，
     * 供检索层做硬过滤（避免仅靠相似度把过期文档召回）。
     */
    private Map<String, Object> payload(Prepared item) {
        KnowledgeChunk chunk = item.row();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("docCode", chunk.getDocCode());
        payload.put("docTitle", chunk.getDocTitle());
        payload.put("category", chunk.getCategory());
        payload.put("seqNo", chunk.getSeqNo());
        payload.put("visibility", chunk.getVisibility());
        payload.put("keywords", chunk.getKeywords());
        payload.put("content", chunk.getContent());
        payload.put("sectionPath", item.piece().sectionPath());
        // 父子块：子块用于检索，父块用于回填上下文；单块章节没有独立父块
        payload.put("parentId", item.piece().parentContent() == null
                ? "" : chunk.getDocCode() + "#" + item.piece().sectionNo());
        payload.put("parentContent", item.piece().parentContent() == null
                ? "" : item.piece().parentContent());
        payload.put("version", chunk.getVersion());
        payload.put("status", chunk.getStatus());
        payload.put("effectiveFrom", chunk.getEffectiveFrom() == null ? "" : chunk.getEffectiveFrom().toString());
        payload.put("effectiveTo", chunk.getEffectiveTo() == null ? "" : chunk.getEffectiveTo().toString());
        payload.put("effectiveFromDay", chunk.getEffectiveFrom() == null
                ? MIN_EPOCH_DAY : chunk.getEffectiveFrom().toEpochDay());
        payload.put("effectiveToDay", chunk.getEffectiveTo() == null
                ? MAX_EPOCH_DAY : chunk.getEffectiveTo().toEpochDay());
        payload.put("reviewDueDay", chunk.getReviewDueAt() == null
                ? MAX_EPOCH_DAY : chunk.getReviewDueAt().toEpochDay());
        return payload;
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add(value);
        }
        return list;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("计算文档哈希失败：" + e.getMessage(), e);
        }
    }

    private String titleOf(List<String> lines) {
        return lines.stream()
                .filter(line -> line.startsWith("# "))
                .findFirst()
                .map(line -> line.substring(2).trim())
                .orElse("HR 政策");
    }

    /** 正文：保留二级及以下标题供结构切分，去掉文档标题与元信息行 */
    private String bodyOf(List<String> lines) {
        return lines.stream()
                .filter(line -> !line.startsWith("# "))
                .filter(line -> !line.startsWith("> 分类:"))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 解析文档元信息行：
     * {@code > 分类: x | 可见性: y | 关键词: a,b | 版本: 1 | 生效日期: 2026-01-01 | 失效日期: 2026-12-31 | 复审日期: 2027-01-01 | 状态: ACTIVE}
     */
    private DocMeta metaOf(List<String> lines) {
        String metaLine = lines.stream().filter(line -> line.startsWith("> 分类:")).findFirst().orElse("");
        Map<String, String> kv = new LinkedHashMap<>();
        for (String part : metaLine.replaceFirst("^>", "").split("\\|")) {
            String[] pair = part.split(":", 2);
            if (pair.length == 2) {
                kv.put(pair[0].trim(), pair[1].trim());
            }
        }
        return new DocMeta(
                kv.getOrDefault("分类", "通用"),
                kv.getOrDefault("可见性", KnowledgeChunk.VISIBILITY_PUBLIC),
                kv.getOrDefault("关键词", ""),
                parseInt(kv.get("版本"), 1),
                parseDate(kv.get("生效日期")),
                parseDate(kv.get("失效日期")),
                parseDate(kv.get("复审日期")),
                kv.getOrDefault("状态", KnowledgeChunk.STATUS_ACTIVE).toUpperCase());
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return value == null ? defaultValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
        } catch (Exception e) {
            log.warn("文档元信息日期格式非法，已忽略：{}", value);
            return null;
        }
    }

    /** 源文档解析结果 */
    private record SourceDoc(String docCode, String title, String body, String hash, DocMeta meta) {
    }

    /** 待入库片段：数据库行 + 结构切分信息（章节路径、父块） */
    private record Prepared(KnowledgeChunk row, MarkdownChunker.Chunk piece) {
    }

    /** 文档元信息（分类/可见性/关键词 + 时效治理字段） */
    private record DocMeta(String category,
                           String visibility,
                           String keywords,
                           int version,
                           LocalDate effectiveFrom,
                           LocalDate effectiveTo,
                           LocalDate reviewDueAt,
                           String status) {
    }
}
