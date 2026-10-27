package org.example.hragent.agent.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 知识库时效治理字段的启动迁移。
 * <p>
 * 项目未引入 Flyway/Liquibase，已部署库升级时按 information_schema 校验并补齐 t_knowledge_chunk
 * 的新增列（版本/状态/有效期/复审日期/内容哈希），只做加法，不修改或删除既有数据。
 * 全新库仍以 {@code sql/rag_knowledge_ddl.sql} 为准。
 * 迁移失败只记录日志，不阻断应用启动。
 */
@Component
@Order(0)
public class RagSchemaMigrator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RagSchemaMigrator.class);

    private static final String TABLE = "t_knowledge_chunk";

    private final JdbcTemplate jdbc;

    public RagSchemaMigrator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            if (count("SELECT COUNT(*) FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?", TABLE) == 0) {
                log.warn("未找到 {} 表，跳过时效治理字段迁移（请先执行 sql/rag_knowledge_ddl.sql）", TABLE);
                return;
            }
            addColumnIfMissing("version", "INT DEFAULT 1");
            addColumnIfMissing("status", "VARCHAR(16) DEFAULT 'ACTIVE'");
            addColumnIfMissing("effective_from", "DATE DEFAULT NULL");
            addColumnIfMissing("effective_to", "DATE DEFAULT NULL");
            addColumnIfMissing("review_due_at", "DATE DEFAULT NULL");
            addColumnIfMissing("source_hash", "CHAR(64) DEFAULT NULL");
            addColumnIfMissing("superseded_by", "INT DEFAULT NULL");

            // 历史数据回填：状态/版本为空会导致检索过滤失效
            jdbc.update("UPDATE " + TABLE + " SET status = 'ACTIVE' WHERE status IS NULL");
            jdbc.update("UPDATE " + TABLE + " SET version = 1 WHERE version IS NULL");

            addIndexIfMissing("idx_doc_version", "(`doc_code`, `version`)");
            addIndexIfMissing("idx_status_review", "(`status`, `review_due_at`)");
        } catch (Exception e) {
            log.error("知识库时效治理字段迁移失败（不影响启动，请手工执行 DDL）：{}", e.getMessage(), e);
        }
    }

    private void addColumnIfMissing(String column, String definition) {
        if (count("SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?", TABLE, column) > 0) {
            return;
        }
        jdbc.execute("ALTER TABLE `" + TABLE + "` ADD COLUMN `" + column + "` " + definition);
        log.info("知识库表已补齐字段：{}", column);
    }

    private void addIndexIfMissing(String indexName, String columns) {
        if (count("SELECT COUNT(*) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                TABLE, indexName) > 0) {
            return;
        }
        jdbc.execute("ALTER TABLE `" + TABLE + "` ADD INDEX `" + indexName + "` " + columns);
        log.info("知识库表已补齐索引：{}", indexName);
    }

    private int count(String sql, String... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, (Object[]) args);
        return value == null ? 0 : value;
    }
}
