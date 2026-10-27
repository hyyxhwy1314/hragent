package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 知识库文档片段（RAG 检索的原文与元数据）。
 * <p>
 * 原文与元数据存 MySQL，向量存 Qdrant；{@code embeddingId} 关联两侧。
 * 文档采用版本化治理：同一 {@code docCode} 下仅最新版本可为 ACTIVE，
 * 旧版本置 SUPERSEDED（保留审计），过期版本置 EXPIRED（不可检索）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_knowledge_chunk")
public class KnowledgeChunk extends BaseEntity {

    /** 可见性：全员可见 */
    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    /** 可见性：仅 HR 角色可见 */
    public static final String VISIBILITY_HR = "HR";

    /** 文档状态：生效中（唯一可被检索的状态） */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 文档状态：已被新版本替代（保留审计，不可检索） */
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    /** 文档状态：已过有效期或复审超期（不可检索） */
    public static final String STATUS_EXPIRED = "EXPIRED";
    /** 文档状态：草稿（未发布，不可检索） */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 文档编码 */
    @TableField("doc_code")
    private String docCode;

    /** 文档标题（引用展示用） */
    @TableField("doc_title")
    private String docTitle;

    /** 知识分类，如 考勤/请假/薪酬 */
    @TableField("category")
    private String category;

    /** 片段在文档内的序号 */
    @TableField("seq_no")
    private Integer seqNo;

    /** 片段原文 */
    @TableField("content")
    private String content;

    /** 关键词，逗号分隔（用于混合检索） */
    @TableField("keywords")
    private String keywords;

    /** 可见性 PUBLIC=全员 HR=仅HR角色 */
    @TableField("visibility")
    private String visibility;

    /** Qdrant 中的向量点ID */
    @TableField("embedding_id")
    private String embeddingId;

    /** 文档版本号，同一 docCode 内容变更时递增 */
    @TableField("version")
    private Integer version;

    /** 文档状态 ACTIVE/SUPERSEDED/EXPIRED/DRAFT，仅 ACTIVE 可被检索 */
    @TableField("status")
    private String status;

    /** 生效日期（为空视为长期有效，入库时归一为最小日期） */
    @TableField("effective_from")
    private LocalDate effectiveFrom;

    /** 失效日期（为空视为长期有效，入库时归一为最大日期） */
    @TableField("effective_to")
    private LocalDate effectiveTo;

    /** 复审截止日期，超过后加宽限期即自动失效 */
    @TableField("review_due_at")
    private LocalDate reviewDueAt;

    /** 文档内容哈希（SHA-256），用于增量入库变更检测 */
    @TableField("source_hash")
    private String sourceHash;

    /** 替代当前版本的版本号（审计用） */
    @TableField("superseded_by")
    private Integer supersededBy;
}
