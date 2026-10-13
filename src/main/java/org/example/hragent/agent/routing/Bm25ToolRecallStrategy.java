package org.example.hragent.agent.routing;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 BM25 的工具召回策略（纯本地、零外部依赖）。
 * <p>
 * 索引文本由「工具名 + 命名空间(加权) + 标签(加权) + 描述」构成；
 * 中文按「单字 + 二元组」切分，英文/数字按连续串切分，兼顾中英混合查询。
 * 命中业务域的工具会获得加权，提升同域工具的排序。
 * <p>
 * 索引按候选集签名缓存，工具集变化时自动重建。
 */
@Component
public class Bm25ToolRecallStrategy implements ToolRecallStrategy {

    private static final double K1 = 1.2;
    private static final double B = 0.75;
    /** 命中业务域的加权分 */
    private static final double DOMAIN_BOOST = 1.5;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+|[\\u4e00-\\u9fa5]+");

    private volatile Index index;

    @Override
    public List<Scored> recall(String query, Collection<ToolCard> candidates, Set<String> domains, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }
        Index idx = indexOf(candidates);
        Map<String, Integer> queryTermFreq = termFrequency(queryTerms);

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < idx.cards.size(); i++) {
            double score = idx.score(i, queryTermFreq);
            if (score <= 0) {
                continue;
            }
            ToolCard card = idx.cards.get(i);
            if (card.inDomains(domains)) {
                score += DOMAIN_BOOST;
            }
            scored.add(new Scored(card, score));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return scored.size() > topK ? new ArrayList<>(scored.subList(0, topK)) : scored;
    }

    private Index indexOf(Collection<ToolCard> candidates) {
        String signature = signatureOf(candidates);
        Index current = index;
        if (current != null && current.signature.equals(signature)) {
            return current;
        }
        Index rebuilt = buildIndex(List.copyOf(candidates), signature);
        index = rebuilt;
        return rebuilt;
    }

    private Index buildIndex(List<ToolCard> cards, String signature) {
        List<Map<String, Integer>> termFreqs = new ArrayList<>(cards.size());
        int[] lengths = new int[cards.size()];
        Map<String, Integer> documentFreq = new HashMap<>();
        long totalLength = 0;
        for (int i = 0; i < cards.size(); i++) {
            List<String> tokens = tokenize(documentOf(cards.get(i)));
            termFreqs.add(termFrequency(tokens));
            lengths[i] = tokens.size();
            totalLength += tokens.size();
            termFreqs.get(i).keySet().forEach(term -> documentFreq.merge(term, 1, Integer::sum));
        }
        double avgLength = cards.isEmpty() ? 1 : (double) totalLength / cards.size();
        return new Index(signature, cards, termFreqs, lengths, documentFreq, Math.max(avgLength, 1));
    }

    private String signatureOf(Collection<ToolCard> candidates) {
        Set<String> names = new TreeSet<>();
        candidates.forEach(card -> names.add(card.name()));
        return String.join("|", names);
    }

    /**
     * 切分文本为检索词：英文/数字串整体保留；中文串额外展开为单字 + 二元组。
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            appendToken(tokens, matcher.group());
        }
        return tokens;
    }

    private void appendToken(List<String> tokens, String token) {
        if (isCjk(token)) {
            for (int i = 0; i < token.length(); i++) {
                tokens.add(String.valueOf(token.charAt(i)));
                if (i + 1 < token.length()) {
                    tokens.add(token.substring(i, i + 2));
                }
            }
            return;
        }
        tokens.add(token);
    }

    private boolean isCjk(String token) {
        char first = token.charAt(0);
        return first >= '\u4e00' && first <= '\u9fa5';
    }

    private Map<String, Integer> termFrequency(List<String> tokens) {
        Map<String, Integer> freq = new HashMap<>();
        tokens.forEach(token -> freq.merge(token, 1, Integer::sum));
        return freq;
    }

    /** 构建单个工具的索引文本：命名空间与标签重复一次以提升权重 */
    private static String documentOf(ToolCard card) {
        StringBuilder sb = new StringBuilder();
        append(sb, card.name());
        append(sb, card.namespace());
        append(sb, card.namespace());
        append(sb, String.join(" ", card.tags()));
        append(sb, String.join(" ", card.tags()));
        append(sb, card.description());
        return sb.toString();
    }

    private static void append(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(value).append(' ');
        }
    }

    /**
     * 不可变 BM25 索引快照。
     */
    private static final class Index {
        private final String signature;
        private final List<ToolCard> cards;
        private final List<Map<String, Integer>> termFreqs;
        private final int[] lengths;
        private final Map<String, Integer> documentFreq;
        private final double avgLength;

        private Index(String signature, List<ToolCard> cards, List<Map<String, Integer>> termFreqs,
                      int[] lengths, Map<String, Integer> documentFreq, double avgLength) {
            this.signature = signature;
            this.cards = cards;
            this.termFreqs = termFreqs;
            this.lengths = lengths;
            this.documentFreq = documentFreq;
            this.avgLength = avgLength;
        }

        private double score(int docIndex, Map<String, Integer> queryTermFreq) {
            Map<String, Integer> termFreq = termFreqs.get(docIndex);
            int length = lengths[docIndex];
            double total = 0;
            for (Map.Entry<String, Integer> entry : queryTermFreq.entrySet()) {
                Integer tf = termFreq.get(entry.getKey());
                if (tf == null || tf == 0) {
                    continue;
                }
                int df = documentFreq.getOrDefault(entry.getKey(), 0);
                double idf = Math.log(1 + (cards.size() - df + 0.5) / (df + 0.5));
                double denominator = tf + K1 * (1 - B + B * length / avgLength);
                total += idf * (tf * (K1 + 1)) / denominator;
            }
            return total;
        }
    }
}