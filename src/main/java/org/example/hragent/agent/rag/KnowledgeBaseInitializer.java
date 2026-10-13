package org.example.hragent.agent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.example.hragent.agent.rag.QdrantVectorClient.VectorPoint;
import org.example.hragent.entity.KnowledgeChunk;
import org.example.hragent.service.KnowledgeChunkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库初始化：启动时把内置 HR 政策文档切分、向量化并写入 Qdrant 与 MySQL。
 * <p>
 * 幂等策略：MySQL 已有片段且未开启重建时跳过；重建会清空旧片段与旧集合后重新入库。
 * 初始化失败只记录日志，不影响应用启动与其它功能。
 */
@Component
public class KnowledgeBaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseInitializer.class);

    /** 内置知识文档目录（classpath） */
    private static final String DOC_ROOT = "knowledge";

    /** 内置知识文档清单 */
    private static final List<String> DOC_FILES = List.of(
            "attendance.md",
            "leave.md",
            "probation.md",
            "performance.md",
            "salary.md",
            "conduct.md"
    );

    /** 单次向量化请求的片段数（受服务端批量上限约束） */
    private static final int EMBED_BATCH = 8;

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
        long existing = chunkService.count();
        if (!props.isReindex() && existing > 0) {
            log.info("知识库已存在 {} 个片段，跳过初始化（如需重建请设置 hragent.rag.reindex=true）", existing);
            return;
        }
        ingest();
    }

    private void ingest() {
        try {
            if (props.isReindex()) {
                chunkService.truncateAll();
                vectorClient.recreateCollection();
            } else {
                vectorClient.ensureCollection();
            }

            List<KnowledgeChunk> chunks = loadChunks();
            if (chunks.isEmpty()) {
                log.warn("未加载到任何知识文档，跳过向量化入库");
                return;
            }
            vectorClient.upsert(embedAll(chunks));
            chunkService.saveBatch(chunks);
            log.info("知识库初始化完成：{} 篇文档切分为 {} 个片段并已向量化入库",
                    DOC_FILES.size(), chunks.size());
        } catch (Exception e) {
            log.error("知识库初始化失败（不影响其它功能）：{}", e.getMessage(), e);
        }
    }

    /** 读取内置文档并切分为片段 */
    private List<KnowledgeChunk> loadChunks() {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (String file : DOC_FILES) {
            chunks.addAll(parseDoc(file));
        }
        return chunks;
    }

    private List<KnowledgeChunk> parseDoc(String file) {
        String raw = readResource(DOC_ROOT + "/" + file);
        if (raw == null) {
            return List.of();
        }
        List<String> lines = raw.lines().toList();
        String docCode = file.replace(".md", "");
        String title = titleOf(lines);
        DocMeta meta = metaOf(lines);
        List<String> pieces = TextSplitter.split(bodyOf(lines), props.getChunkSize(), props.getChunkOverlap());

        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocCode(docCode);
            chunk.setDocTitle(title);
            chunk.setCategory(meta.category());
            chunk.setSeqNo(i);
            chunk.setContent(pieces.get(i));
            chunk.setKeywords(meta.keywords());
            chunk.setVisibility(meta.visibility());
            chunk.setEmbeddingId(UUID.randomUUID().toString());
            chunks.add(chunk);
        }
        return chunks;
    }

    /** 分批向量化片段，生成待写入 Qdrant 的点 */
    private List<VectorPoint> embedAll(List<KnowledgeChunk> chunks) {
        List<VectorPoint> points = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i += EMBED_BATCH) {
            List<KnowledgeChunk> batch = chunks.subList(i, Math.min(chunks.size(), i + EMBED_BATCH));
            List<TextSegment> segments = batch.stream().map(c -> TextSegment.from(c.getContent())).toList();
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            for (int j = 0; j < batch.size(); j++) {
                KnowledgeChunk chunk = batch.get(j);
                points.add(new VectorPoint(chunk.getEmbeddingId(), toFloatList(embeddings.get(j).vector()),
                        payload(chunk)));
            }
        }
        return points;
    }

    private Map<String, Object> payload(KnowledgeChunk chunk) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("docCode", chunk.getDocCode());
        payload.put("docTitle", chunk.getDocTitle());
        payload.put("category", chunk.getCategory());
        payload.put("seqNo", chunk.getSeqNo());
        payload.put("visibility", chunk.getVisibility());
        payload.put("keywords", chunk.getKeywords());
        payload.put("content", chunk.getContent());
        return payload;
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add(value);
        }
        return list;
    }

    private String readResource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("读取知识文档失败：{} - {}", path, e.getMessage());
            return null;
        }
    }

    private String titleOf(List<String> lines) {
        return lines.stream()
                .filter(line -> line.startsWith("# "))
                .findFirst()
                .map(line -> line.substring(2).trim())
                .orElse("HR 政策");
    }

    private String bodyOf(List<String> lines) {
        return lines.stream()
                .filter(line -> !line.startsWith("#"))
                .filter(line -> !line.startsWith(">"))
                .collect(Collectors.joining("\n"));
    }

    /** 解析文档元信息行：{@code > 分类: x | 可见性: y | 关键词: a,b} */
    private DocMeta metaOf(List<String> lines) {
        String metaLine = lines.stream().filter(line -> line.startsWith(">")).findFirst().orElse("");
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
                kv.getOrDefault("关键词", ""));
    }

    private record DocMeta(String category, String visibility, String keywords) {
    }
}
