-- ============================================================
-- 知识库检索（RAG）增量 DDL
-- 存储分层：原文与元数据存 MySQL（本表），向量存 Qdrant。
-- 表 t_knowledge_chunk：知识库文档的切分片段（片段原文 + 元数据 + 向量点ID）
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_knowledge_chunk` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `doc_code`     VARCHAR(64)  NOT NULL                COMMENT '文档编码',
    `doc_title`    VARCHAR(128) NOT NULL                COMMENT '文档标题（检索结果引用展示用）',
    `category`     VARCHAR(64)  DEFAULT NULL            COMMENT '知识分类，如 考勤/请假/薪酬',
    `seq_no`       INT          DEFAULT 0               COMMENT '片段在文档内的序号',
    `content`      TEXT         NOT NULL                COMMENT '片段原文',
    `keywords`     VARCHAR(255) DEFAULT NULL            COMMENT '关键词，逗号分隔（用于混合检索）',
    `visibility`   VARCHAR(16)  DEFAULT 'PUBLIC'        COMMENT '可见性 PUBLIC=全员 HR=仅HR角色',
    `embedding_id` CHAR(36)     DEFAULT NULL            COMMENT 'Qdrant 中的向量点ID',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`  DATETIME     DEFAULT NULL,
    `is_deleted`   TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_doc_seq` (`doc_code`, `seq_no`),
    KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档片段（RAG 原文与元数据）';
