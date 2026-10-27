package org.example.hragent.agent.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.example.hragent.agent.state.ToolCallRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 本地轻量模型实现的「只读工具规划」策略（分层 Tool Routing 第 4 层「模型精选」的本地实现）。
 * <p>
 * 只让本地模型对<b>只读/查询类</b>候选工具完成「选工具 + 生成参数」这一跳——写操作与流程类工具
 * 不在候选内（本类只保留 {@link ToolCard#readOnly()} 的候选），仍由远程模型选定，从而在省去一次
 * 远程调用的同时把本地模型的能力风险限制在只读查询上。任何异常（调用失败、超时、输出非法、
 * 仅含候选外工具或空规划）都返回空结果，交回远程模型自选，回退逻辑内聚在本类，上层无需条件分支。
 */
public class LocalModelToolPlanningStrategy implements ToolPlanningStrategy {

    private static final Logger log = LoggerFactory.getLogger(LocalModelToolPlanningStrategy.class);

    /** 单轮最多规划的工具数（与提示词中的上限保持一致） */
    private static final int MAX_PLANNED = 3;

    private static final String SYSTEM_PROMPT = """
            你是 HR 系统的工具调用规划器。根据用户请求，从候选工具中选择需要调用的工具并给出参数。
            只输出 JSON 数组，元素形如 {"name":"工具名","arguments":{"参数名":值}}。
            要求：
            - 只能从候选工具中选择，不得编造工具名
            - 最多选择 %d 个工具
            - 参数值从用户请求中提取，缺失的参数不要臆造
            - 若无需调用任何工具（如闲聊），输出 []
            示例：
            用户：把研发部的人都列出来 -> [{"name":"queryEmployeeList","arguments":{"department":"研发部"}}]
            用户：你好 -> []
            只输出 JSON 数组，不要解释，不要代码块标记。
            """;

    private final ChatLanguageModel model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalModelToolPlanningStrategy(ChatLanguageModel model) {
        this.model = model;
    }

    @Override
    public Optional<List<ToolCallRecord>> plan(String query, List<ToolCard> candidates, Set<String> domains) {
        return Optional.ofNullable(query)
                .filter(text -> !text.isBlank())
                .flatMap(text -> readOnlyCandidates(candidates))
                .flatMap(readOnly -> planByModel(query, readOnly));
    }

    /** 仅保留只读候选：写操作与流程类工具继续交由远程模型选定 */
    private Optional<List<ToolCard>> readOnlyCandidates(List<ToolCard> candidates) {
        List<ToolCard> readOnly = Optional.ofNullable(candidates).orElse(List.of()).stream()
                .filter(ToolCard::readOnly)
                .toList();
        return readOnly.isEmpty() ? Optional.empty() : Optional.of(readOnly);
    }

    /** 调用本地模型做规划；调用失败或输出不可用时返回空 Optional 以触发回退 */
    private Optional<List<ToolCallRecord>> planByModel(String query, List<ToolCard> candidates) {
        try {
            String answer = model.generate(
                    new SystemMessage(SYSTEM_PROMPT.formatted(MAX_PLANNED)),
                    new UserMessage(buildUserPrompt(query, candidates))).content().text();
            return parsePlan(answer, candidates);
        } catch (Exception e) {
            log.warn("本地工具规划调用失败，交回远程模型自选：{}", e.getMessage());
            return Optional.empty();
        }
    }

    private String buildUserPrompt(String query, List<ToolCard> candidates) {
        String tools = candidates.stream().map(this::describe).collect(Collectors.joining("\n"));
        return "候选工具：\n" + tools + "\n\n用户请求：" + query;
    }

    /** 用「工具名 + 一句话描述 + 参数名」描述候选，容量小且足够本地模型判断 */
    private String describe(ToolCard card) {
        return "- " + card.name() + "：" + card.description() + "（参数：" + parameterNames(card) + "）";
    }

    private String parameterNames(ToolCard card) {
        ToolSpecification spec = card.specification();
        if (spec.parameters() == null || spec.parameters().properties() == null
                || spec.parameters().properties().isEmpty()) {
            return "无";
        }
        return String.join("、", spec.parameters().properties().keySet());
    }

    /** 解析模型输出的 JSON 数组：过滤候选外工具、截取上限数量，产出可执行的工具调用记录 */
    private Optional<List<ToolCallRecord>> parsePlan(String answer, List<ToolCard> candidates) {
        try {
            JsonNode array = objectMapper.readTree(jsonArrayIn(answer));
            if (!array.isArray() || array.isEmpty()) {
                return Optional.empty();
            }
            Set<String> allowed = candidates.stream().map(ToolCard::name).collect(Collectors.toSet());
            List<ToolCallRecord> records = new ArrayList<>();
            for (JsonNode node : array) {
                if (records.size() >= MAX_PLANNED) {
                    break;
                }
                JsonNode nameNode = node.get("name");
                String name = nameNode == null ? null : nameNode.asText();
                if (name == null || !allowed.contains(name)) {
                    log.warn("本地工具规划输出候选外工具，已忽略：{}", name);
                    continue;
                }
                records.add(new ToolCallRecord(UUID.randomUUID().toString(), name, argumentsJson(node.get("arguments"))));
            }
            return records.isEmpty() ? Optional.empty() : Optional.of(records);
        } catch (Exception e) {
            log.warn("本地工具规划输出非法，交回远程模型自选：{}", answer);
            return Optional.empty();
        }
    }

    /** 取出 arguments 对象并序列化为 JSON 字符串；缺失或非对象时回退空参数 */
    private String argumentsJson(JsonNode arguments) {
        try {
            return arguments != null && arguments.isObject()
                    ? objectMapper.writeValueAsString(arguments)
                    : "{}";
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 截取文本中最外层的 JSON 数组，兼容模型在数组前后附带说明文字或代码块标记的情况 */
    private String jsonArrayIn(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : text;
    }
}
