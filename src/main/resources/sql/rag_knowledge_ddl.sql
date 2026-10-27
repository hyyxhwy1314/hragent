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
    `version`      INT          DEFAULT 1               COMMENT '文档版本号，内容变更时递增',
    `status`       VARCHAR(16)  DEFAULT 'ACTIVE'        COMMENT '状态 ACTIVE/SUPERSEDED/EXPIRED/DRAFT，仅 ACTIVE 可检索',
    `effective_from` DATE       DEFAULT NULL            COMMENT '生效日期，为空视为长期有效',
    `effective_to`   DATE       DEFAULT NULL            COMMENT '失效日期，为空视为长期有效',
    `review_due_at`  DATE       DEFAULT NULL            COMMENT '复审截止日期，超期加宽限期后自动失效',
    `source_hash`    CHAR(64)   DEFAULT NULL            COMMENT '文档内容 SHA-256，用于增量入库变更检测',
    `superseded_by`  INT        DEFAULT NULL            COMMENT '替代当前版本的版本号（审计）',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `delete_time`  DATETIME     DEFAULT NULL,
    `is_deleted`   TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_doc_seq` (`doc_code`, `seq_no`),
    KEY `idx_doc_version` (`doc_code`, `version`),
    KEY `idx_status_review` (`status`, `review_due_at`),
    KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档片段（RAG 原文与元数据）';

-- ------------------------------------------------------------
-- 已部署库增量升级（应用启动时 RagSchemaMigrator 会自动补齐以下列，
-- 手工执行时按需逐条运行；MySQL 8 不支持 ADD COLUMN IF NOT EXISTS）
-- ------------------------------------------------------------
-- ALTER TABLE `t_knowledge_chunk`
--     ADD COLUMN `version` INT DEFAULT 1 COMMENT '文档版本号，内容变更时递增',
--     ADD COLUMN `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/SUPERSEDED/EXPIRED/DRAFT',
--     ADD COLUMN `effective_from` DATE DEFAULT NULL COMMENT '生效日期',
--     ADD COLUMN `effective_to` DATE DEFAULT NULL COMMENT '失效日期',
--     ADD COLUMN `review_due_at` DATE DEFAULT NULL COMMENT '复审截止日期',
--     ADD COLUMN `source_hash` CHAR(64) DEFAULT NULL COMMENT '文档内容 SHA-256',
--     ADD COLUMN `superseded_by` INT DEFAULT NULL COMMENT '替代版本号',
--     ADD KEY `idx_doc_version` (`doc_code`, `version`),
--     ADD KEY `idx_status_review` (`status`, `review_due_at`);
