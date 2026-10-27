package org.example.hragent.agent.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构感知切分器的边界与复杂场景测试。
 */
class MarkdownChunkerTest {

    private static final int SIZE = 200;
    private static final int OVERLAP = 40;

    @Test
    void 空文档与纯元信息文档不产生片段() {
        assertTrue(MarkdownChunker.split("", SIZE, OVERLAP).isEmpty());
        assertTrue(MarkdownChunker.split("> 分类: 通用 | 可见性: PUBLIC", SIZE, OVERLAP).isEmpty());
        assertTrue(MarkdownChunker.split("# 只有标题", SIZE, OVERLAP).isEmpty());
    }

    @Test
    void 按标题层级切分并携带章节路径() {
        String doc = """
                # 制度
                > 分类: 通用 | 可见性: PUBLIC

                ## 年假
                员工每年享有年假。

                ### 折算规则
                入职不满一年按比例折算。
                """;
        List<MarkdownChunker.Chunk> chunks = MarkdownChunker.split(doc, SIZE, OVERLAP);

        assertEquals(2, chunks.size());
        assertEquals("年假", chunks.get(0).sectionPath());
        assertEquals("年假 > 折算规则", chunks.get(1).sectionPath());
        assertNull(chunks.get(0).parentContent());
    }

    @Test
    void 长章节拆成多个子块并保留完整父块() {
        String longText = "第一段说明适用范围与基本原则。".repeat(12)
                + "\n\n第二段说明审批流程与责任分工。".repeat(1)
                + "\n\n第三段说明违规处理与申诉渠道。".repeat(1);
        String doc = "# 制度\n\n## 长章节\n" + longText;
        List<MarkdownChunker.Chunk> chunks = MarkdownChunker.split(doc, SIZE, OVERLAP);

        assertTrue(chunks.size() > 1);
        assertNotNull(chunks.get(0).parentContent());
        assertTrue(chunks.get(0).parentContent().contains("违规处理"));
        for (MarkdownChunker.Chunk chunk : chunks) {
            assertTrue(chunk.content().length() <= SIZE, "子块超长：" + chunk.content().length());
        }
    }

    @Test
    void 超长表格按行拆分且每个子表重复表头() {
        StringBuilder table = new StringBuilder("| 职级 | 城市 | 住宿标准 |\n| --- | --- | --- |\n");
        for (int i = 1; i <= 30; i++) {
            table.append("| 职级").append(i).append(" | 城市").append(i).append(" | ").append(100 + i).append(" 元/晚 |\n");
        }
        String doc = "# 差旅\n\n## 住宿标准\n" + table;
        List<MarkdownChunker.Chunk> chunks = MarkdownChunker.split(doc, SIZE, OVERLAP);

        assertTrue(chunks.size() > 1);
        for (MarkdownChunker.Chunk chunk : chunks) {
            assertTrue(chunk.content().startsWith("| 职级 | 城市 | 住宿标准 |"), "子表缺少表头");
            assertTrue(chunk.content().contains("| --- | --- | --- |"), "子表缺少分隔行");
            for (String line : chunk.content().split("\n")) {
                if (!line.isBlank()) {
                    assertTrue(line.startsWith("|") && line.endsWith("|"), "表格行列被切断：" + line);
                }
            }
        }
    }

    @Test
    void 无标点超长段落硬切且不超长() {
        String blob = "超长文本".repeat(400);
        String doc = "# 制度\n\n## 说明\n" + blob;
        List<MarkdownChunker.Chunk> chunks = MarkdownChunker.split(doc, SIZE, OVERLAP);

        assertTrue(chunks.size() > 1);
        for (MarkdownChunker.Chunk chunk : chunks) {
            assertTrue(chunk.content().length() <= SIZE);
        }
    }

    @Test
    void 列表条目完整切分且续行跟随所属条目() {
        StringBuilder list = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            list.append("- 第").append(i).append("条要求说明，包含适用范围与执行标准");
            if (i == 1) {
                list.append("\n  续行说明：本条目适用于全体正式员工");
            }
            list.append("\n");
        }
        String doc = "# 制度\n\n## 行为规范\n" + list;
        List<MarkdownChunker.Chunk> chunks = MarkdownChunker.split(doc, SIZE, OVERLAP);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().contains("续行说明")));
        for (MarkdownChunker.Chunk chunk : chunks) {
            for (String line : chunk.content().split("\n")) {
                assertTrue(line.isBlank() || line.startsWith("-") || line.startsWith("  "),
                        "列表条目被断开：" + line);
            }
        }
    }

    @Test
    void 无标题文档仍可切分且不产生章节路径() {
        String doc = "第一段内容。".repeat(30) + "\n\n第二段内容。".repeat(30);
        List<MarkdownChunker.Chunk> chunks = MarkdownChunker.split(doc, SIZE, OVERLAP);

        assertTrue(chunks.size() > 1);
        chunks.forEach(chunk -> assertEquals("", chunk.sectionPath()));
    }

    @Test
    void 代码块作为原子块不被段落切分破坏() {
        String code = "```\n" + "SELECT * FROM t_employee WHERE role = 'HR';\n".repeat(20) + "```";
        String doc = "# 制度\n\n## 示例\n" + code;
        List<MarkdownChunker.Chunk> chunks = MarkdownChunker.split(doc, SIZE, OVERLAP);

        assertTrue(chunks.size() >= 1);
        assertTrue(chunks.get(0).content().contains("SELECT * FROM t_employee"));
    }
}
