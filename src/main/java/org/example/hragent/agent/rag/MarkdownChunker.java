package org.example.hragent.agent.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 结构感知文档切分器（业界主流的 Markdown 切分方式）。
 * <p>
 * 三条原则：
 * <ul>
 *   <li><b>按标题层级切分</b>：每个片段归属一个章节，并携带「一级 &gt; 二级」章节路径，避免跨章节语义串味；</li>
 *   <li><b>原子块</b>：表格、列表、代码块整体切分，表格超长时按行拆分并重复表头，绝不把表格行切成半行；</li>
 *   <li><b>父子块</b>：章节过长时拆成多个子块用于向量检索，同时保留整节正文作为父块，
 *       检索命中子块后回填父块，补足上下文（small-to-big）。</li>
 * </ul>
 * 只有超长段落才按句子边界二次切分，并按配置重叠，保证语义完整。
 */
public final class MarkdownChunker {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*(?:[-*+]|\\d+[.)])\\s+.*$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\s*\\|.*\\|\\s*$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\s*\\|(?:\\s*:?-{3,}:?\\s*\\|)+\\s*$");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。！？；!?;])");

    private MarkdownChunker() {
    }

    /**
     * 切分结果。
     *
     * @param sectionNo     章节序号（同一文档内唯一，用于生成父块 ID）
     * @param sectionPath   章节路径，如「请假管理 &gt; 年假」
     * @param content       子块正文（写入向量库的检索单元）
     * @param parentContent 父块正文（章节完整内容）；章节本身未拆分时为 null
     */
    public record Chunk(int sectionNo, String sectionPath, String content, String parentContent) {
    }

    /**
     * 将 Markdown 文档切分为结构感知片段。
     *
     * @param markdown  原始 Markdown（允许包含标题与元信息行）
     * @param chunkSize 子块目标字符数
     * @param overlap   超长段落按句切分时的重叠字符数
     */
    public static List<Chunk> split(String markdown, int chunkSize, int overlap) {
        String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n");
        List<Section> sections = parse(normalized);
        int size = Math.max(120, chunkSize);
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            String parent = joinBlocks(section.blocks());
            if (parent.isEmpty()) {
                continue;
            }
            if (parent.length() <= size) {
                chunks.add(new Chunk(i, section.path(), parent, null));
                continue;
            }
            for (String piece : pack(section.blocks(), size, overlap)) {
                chunks.add(new Chunk(i, section.path(), piece, parent));
            }
        }
        return chunks;
    }

    /** 解析出章节：标题决定章节边界，块决定切分原子性 */
    private static List<Section> parse(String markdown) {
        List<Section> sections = new ArrayList<>();
        List<String> path = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        BlockKind kind = null;
        boolean inCode = false;

        for (String line : markdown.split("\n", -1)) {
            if (line.startsWith("```")) {
                if (inCode) {
                    buffer.add(line);
                    blocks.add(new Block(BlockKind.CODE, String.join("\n", buffer)));
                    buffer.clear();
                    inCode = false;
                    kind = null;
                } else {
                    flush(blocks, buffer, kind);
                    inCode = true;
                    kind = BlockKind.CODE;
                    buffer.add(line);
                }
                continue;
            }
            if (inCode) {
                buffer.add(line);
                continue;
            }

            var heading = HEADING.matcher(line);
            if (heading.matches()) {
                flush(blocks, buffer, kind);
                kind = null;
                if (!blocks.isEmpty()) {
                    sections.add(new Section(String.join(" > ", path), blocks));
                    blocks = new ArrayList<>();
                }
                int level = heading.group(1).length();
                String title = heading.group(2).trim();
                // 一级标题是文档标题，不进章节路径
                if (level <= 1) {
                    path.clear();
                } else {
                    while (path.size() >= level - 1) {
                        path.remove(path.size() - 1);
                    }
                    path.add(title);
                }
                continue;
            }

            // 文档元信息行（分类/可见性/关键词/版本等）不属于正文
            if (line.startsWith("> 分类:")) {
                continue;
            }
            if (line.isBlank()) {
                flush(blocks, buffer, kind);
                kind = null;
                continue;
            }

            BlockKind current = kindOf(line);
            // 列表的缩进续行属于上一条目，不能当成新段落
            if (kind == BlockKind.LIST && (line.startsWith("  ") || line.startsWith("\t"))) {
                current = BlockKind.LIST;
            }
            if (kind != null && kind != current) {
                flush(blocks, buffer, kind);
            }
            kind = current;
            buffer.add(line);
        }
        flush(blocks, buffer, kind);
        if (!blocks.isEmpty()) {
            sections.add(new Section(String.join(" > ", path), blocks));
        }
        return sections;
    }

    /** 刷出一个原子块：段落压成一行，表格/列表/代码保留换行 */
    private static void flush(List<Block> blocks, List<String> buffer, BlockKind kind) {
        if (buffer.isEmpty() || kind == null) {
            buffer.clear();
            return;
        }
        String text = kind == BlockKind.PARAGRAPH
                ? String.join(" ", buffer).trim()
                : String.join("\n", buffer).trim();
        buffer.clear();
        if (!text.isEmpty()) {
            blocks.add(new Block(kind, text));
        }
    }

    private static BlockKind kindOf(String line) {
        if (TABLE_ROW.matcher(line).matches()) {
            return BlockKind.TABLE;
        }
        if (LIST_ITEM.matcher(line).matches()) {
            return BlockKind.LIST;
        }
        return BlockKind.PARAGRAPH;
    }

    private static String joinBlocks(List<Block> blocks) {
        StringBuilder text = new StringBuilder();
        for (Block block : blocks) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(block.text());
        }
        return text.toString().trim();
    }

    /** 按块打包为不超长的片段：块不拆，超长块按类型二次切分 */
    private static List<String> pack(List<Block> blocks, int size, int overlap) {
        List<String> pieces = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (Block block : blocks) {
            String text = block.text();
            if (text.length() <= size) {
                if (buffer.length() + text.length() + 2 > size) {
                    addIfNotEmpty(pieces, buffer);
                }
                if (buffer.length() > 0) {
                    buffer.append("\n\n");
                }
                buffer.append(text);
                continue;
            }
            addIfNotEmpty(pieces, buffer);
            pieces.addAll(splitBlock(block, size, overlap));
        }
        addIfNotEmpty(pieces, buffer);
        return pieces;
    }

    private static void addIfNotEmpty(List<String> pieces, StringBuilder buffer) {
        String text = buffer.toString().trim();
        buffer.setLength(0);
        if (!text.isEmpty()) {
            pieces.add(text);
        }
    }

    /** 超长块二次切分：表格保表头、列表保条目、段落按句子 */
    private static List<String> splitBlock(Block block, int size, int overlap) {
        return switch (block.kind()) {
            case TABLE -> splitTable(block.text(), size);
            case LIST -> splitList(block.text(), size, overlap);
            case PARAGRAPH, CODE -> splitParagraph(block.text(), size, overlap);
        };
    }

    /** 超长表格按行拆分，每个子表重复表头，保证单行表格语义可读 */
    private static List<String> splitTable(String table, int size) {
        List<String> rows = List.of(table.split("\n"));
        int headerSize = rows.size() > 1 && TABLE_SEPARATOR.matcher(rows.get(1)).matches() ? 2 : 1;
        String header = String.join("\n", rows.subList(0, headerSize));
        List<String> pieces = new ArrayList<>();
        StringBuilder buffer = new StringBuilder(header);
        for (String row : rows.subList(headerSize, rows.size())) {
            if (row.length() > size) {
                addIfNotEmpty(pieces, buffer);
                pieces.addAll(splitParagraph(row, size, 0));
                buffer.append(header);
                continue;
            }
            if (buffer.length() > header.length() && buffer.length() + row.length() + 1 > size) {
                addIfNotEmpty(pieces, buffer);
                buffer.append(header);
            }
            buffer.append('\n').append(row);
        }
        addIfNotEmpty(pieces, buffer);
        return pieces;
    }

    /** 超长列表按条目拆分（续行跟随所属条目） */
    private static List<String> splitList(String list, int size, int overlap) {
        List<String> items = new ArrayList<>();
        for (String line : list.split("\n")) {
            if (LIST_ITEM.matcher(line).matches() || items.isEmpty()) {
                items.add(line);
            } else {
                items.set(items.size() - 1, items.get(items.size() - 1) + "\n" + line);
            }
        }
        List<String> pieces = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String item : items) {
            if (item.length() > size) {
                addIfNotEmpty(pieces, buffer);
                pieces.addAll(splitParagraph(item, size, overlap));
                continue;
            }
            if (buffer.length() + item.length() + 1 > size) {
                addIfNotEmpty(pieces, buffer);
            }
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(item);
        }
        addIfNotEmpty(pieces, buffer);
        return pieces;
    }

    /** 超长段落按句边界切分，片段间保留重叠上下文 */
    private static List<String> splitParagraph(String paragraph, int size, int overlap) {
        List<String> pieces = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String sentence : SENTENCE_END.split(paragraph)) {
            if (sentence.isBlank()) {
                continue;
            }
            if (sentence.length() > size) {
                addIfNotEmpty(pieces, buffer);
                pieces.addAll(hardSplit(sentence.trim(), size, overlap));
                continue;
            }
            if (buffer.length() + sentence.length() > size) {
                String previous = buffer.toString().trim();
                buffer.setLength(0);
                if (!previous.isEmpty()) {
                    pieces.add(previous);
                    if (overlap > 0) {
                        buffer.append(tail(previous, overlap));
                    }
                }
            }
            buffer.append(sentence);
        }
        addIfNotEmpty(pieces, buffer);
        return pieces;
    }

    /** 无标点超长文本按窗口硬切 */
    private static List<String> hardSplit(String text, int size, int overlap) {
        List<String> pieces = new ArrayList<>();
        int step = Math.max(1, size - overlap);
        for (int start = 0; start < text.length(); start += step) {
            pieces.add(text.substring(start, Math.min(text.length(), start + size)).trim());
        }
        return pieces;
    }

    private static String tail(String text, int overlap) {
        return text.length() <= overlap ? text : text.substring(text.length() - overlap);
    }

    /** 原子块类型 */
    private enum BlockKind {
        PARAGRAPH, LIST, TABLE, CODE
    }

    private record Block(BlockKind kind, String text) {
    }

    private record Section(String path, List<Block> blocks) {
    }
}
