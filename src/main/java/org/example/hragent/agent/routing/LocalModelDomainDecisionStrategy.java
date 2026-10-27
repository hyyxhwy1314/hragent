package org.example.hragent.agent.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 本地轻量模型实现的业务域决策策略（分层 Tool Routing 第 3 层的模型实现）。
 * <p>
 * 只让本地模型输出「业务域标签集合」这一短结构化结果，工具参数生成与最终作答仍由远程模型负责，
 * 因此本地模型能力不足时影响面最小。任何异常（调用失败、超时、输出非法或空结果）都回退到
 * 规则策略，保证路由链路始终可用——回退逻辑内聚在本类，上层无需条件分支。
 */
public class LocalModelDomainDecisionStrategy implements DomainDecisionStrategy {

    private static final Logger log = LoggerFactory.getLogger(LocalModelDomainDecisionStrategy.class);

    private static final String SYSTEM_PROMPT = """
            你是 HR 系统的工具路由分类器。判断用户请求涉及哪些业务域，只输出 JSON 字符串数组。
            可选标签：%s
            判定规则：
            - 请假/年假/假期/余额/证明/合同到期/提醒 归「自助」
            - 导出/花名册/表格/工资表/通知书 归「文档」
            - 入职/离职/调岗/转正/审批/待办 归「流程」
            - 查员工信息/人员列表/部门人员 归「员工」
            - 招聘/发布职位 归「岗位」；绩效考核/评分 归「绩效」
            - 制度/政策/规定/手册 归「知识」；课程/学习/技能 归「培训」
            示例：
            用户：我想申请下个月休一周 -> ["自助"]
            用户：帮我导出这个月的花名册 -> ["文档"]
            用户：把研发部的人都列出来 -> ["员工"]
            用户：你好 -> []
            无法判断时输出空数组 []。不要输出解释或多余文字。
            """;

    private final ChatLanguageModel model;
    private final DomainDecisionStrategy fallback;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> supported = ToolClassifier.supportedDomains();

    public LocalModelDomainDecisionStrategy(ChatLanguageModel model, DomainDecisionStrategy fallback) {
        this.model = model;
        this.fallback = fallback;
    }

    @Override
    public Set<String> decide(String query) {
        return Optional.ofNullable(query)
                .filter(text -> !text.isBlank())
                .flatMap(this::decideByModel)
                .orElseGet(() -> fallback.decide(query));
    }

    /** 调用本地模型做域分类；调用失败或输出不可用时返回空 Optional 以触发回退 */
    private Optional<Set<String>> decideByModel(String query) {
        try {
            String answer = model.generate(
                    new SystemMessage(String.format(SYSTEM_PROMPT, String.join(",", supported))),
                    new UserMessage(query)).content().text();
            return parseDomains(answer);
        } catch (Exception e) {
            log.warn("本地决策模型调用失败，回退规则分类：{}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Set<String>> parseDomains(String answer) {
        return Optional.ofNullable(answer).map(String::strip).flatMap(this::toDomains);
    }

    /** 解析模型输出的 JSON 数组并过滤非法标签；无有效标签时返回空 Optional 以触发回退 */
    private Optional<Set<String>> toDomains(String text) {
        try {
            String[] labels = objectMapper.readValue(jsonArrayIn(text), String[].class);
            Set<String> domains = Arrays.stream(labels)
                    .filter(supported::contains)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return domains.isEmpty() ? Optional.empty() : Optional.of(domains);
        } catch (Exception e) {
            log.warn("本地决策模型输出非法，回退规则分类：{}", text);
            return Optional.empty();
        }
    }

    /** 截取文本中最外层的 JSON 数组，兼容模型在数组前后附带说明文字或代码块标记的情况 */
    private String jsonArrayIn(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : text;
    }
}
