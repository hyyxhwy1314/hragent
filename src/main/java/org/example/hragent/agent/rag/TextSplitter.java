package org.example.hragent.agent.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档切分器：按段落聚合为定长片段，超长段落按窗口滑动切分。
 * <p>
 * 用于把知识库文档切分成适合向量化的语义片段。
 */
public final class TextSplitter {

    private TextSplitter() {
    }

    /**
     * 将文本切分为若干片段。
     *
     * @param text      原始文本（空行分隔段落）
     * @param chunkSize 单个片段目标字符数
     * @param overlap   相邻片段重叠字符数
     */
    public static List<String> split(String text, int chunkSize, int overlap) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String block : normalized.split("\\n\\s*\\n")) {
            String paragraph = block.replace("\n", " ").trim();
            if (!paragraph.isEmpty()) {
                appendParagraph(chunks, buffer, paragraph, chunkSize, overlap);
            }
        }
        flush(chunks, buffer);
        return chunks;
    }

    private static void appendParagraph(List<String> chunks, StringBuilder buffer,
                                        String paragraph, int chunkSize, int overlap) {
        if (paragraph.length() > chunkSize) {
            String previous = flush(chunks, buffer);
            buffer.append(tail(previous, overlap));
            hardSplit(chunks, paragraph, chunkSize, overlap);
            return;
        }
        if (buffer.length() + paragraph.length() + 1 > chunkSize) {
            String previous = flush(chunks, buffer);
            buffer.append(tail(previous, overlap));
        }
        if (buffer.length() > 0) {
            buffer.append(' ');
        }
        buffer.append(paragraph);
    }

    private static void hardSplit(List<String> chunks, String text, int chunkSize, int overlap) {
        int step = Math.max(1, chunkSize - overlap);
        for (int start = 0; start < text.length(); start += step) {
            chunks.add(text.substring(start, Math.min(text.length(), start + chunkSize)).trim());
        }
    }

    /** 追加缓冲区内容为一个片段并清空，返回该片段内容 */
    private static String flush(List<String> chunks, StringBuilder buffer) {
        String content = buffer.toString().trim();
        buffer.setLength(0);
        if (!content.isEmpty()) {
            chunks.add(content);
        }
        return content;
    }

    /** 取上一片段末尾 overlap 个字符作为下一片段的上下文重叠 */
    private static String tail(String content, int overlap) {
        return content.length() <= overlap ? content : content.substring(content.length() - overlap);
    }
}
