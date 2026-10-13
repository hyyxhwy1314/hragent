package org.example.hragent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库文档片段（RAG 检索的原文与元数据）。
 * <p>
 * 原文与元数据存 MySQL，向量存 Qdrant；{@code embeddingId} 关联两侧。
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
}
