package org.example.hragent.agent.tools;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具过滤器：根据用户查询关键词，动态筛选出相关工具规格，
 * 避免每次 LLM 推理时注入全部 20 个工具，从而节省 token。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>关键词匹配：简单高效，无需额外 LLM 调用</li>
 *   <li>安全兜底：未匹配到任何分类时，返回全部工具（不遗漏）</li>
 *   <li>员工工具作为基础工具，其他分类匹配时自动附带</li>
 * </ul>
 */
public class ToolFilter {

    /** 工具分类与匹配关键词映射 */
    private static final Map<String, Set<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    /** 每个工具归属的分类（一个工具可属于多个分类） */
    private static final Map<String, Set<String>> TOOL_CATEGORIES = new HashMap<>();

    static {
        // 定义分类关键词 — 权重高的分类在前，优先匹配
        CATEGORY_KEYWORDS.put("员工", Set.of("员工", "雇员", "人员", "同事", "工号"));
        CATEGORY_KEYWORDS.put("简历", Set.of("简历", "候选人", "登记", "录用", "面试", "投递", "匹配"));
        CATEGORY_KEYWORDS.put("流程", Set.of("流程", "入职", "离职", "调岗", "转正", "审批", "待办",
                "任务", "撤回", "通过", "拒绝", "轨迹", "发起", "实例"));
        CATEGORY_KEYWORDS.put("岗位", Set.of("岗位", "职位", "招聘", "招人", "发布"));
        CATEGORY_KEYWORDS.put("绩效", Set.of("绩效", "考核", "评分", "报告"));
        CATEGORY_KEYWORDS.put("培训", Set.of("培训", "课程", "学习", "技能"));
        CATEGORY_KEYWORDS.put("知识", Set.of("政策", "制度", "规定", "规则", "知识", "手册", "规章"));

        // 工具名称 → 归属分类
        TOOL_CATEGORIES.put("queryEmployeeInfo", Set.of("员工"));
        TOOL_CATEGORIES.put("queryEmployeeList", Set.of("员工"));
        TOOL_CATEGORIES.put("queryJobInfo", Set.of("岗位"));
        TOOL_CATEGORIES.put("queryJobList", Set.of("岗位"));
        TOOL_CATEGORIES.put("queryResumeInfo", Set.of("简历"));
        TOOL_CATEGORIES.put("queryResumeMatch", Set.of("简历"));
        TOOL_CATEGORIES.put("registerCandidate", Set.of("简历"));
        TOOL_CATEGORIES.put("acceptCandidate", Set.of("简历"));
        TOOL_CATEGORIES.put("startOnboardingProcess", Set.of("简历", "流程"));
        TOOL_CATEGORIES.put("startResignationProcess", Set.of("流程"));
        TOOL_CATEGORIES.put("startTransferProcess", Set.of("流程"));
        TOOL_CATEGORIES.put("queryProcessInstances", Set.of("流程"));
        TOOL_CATEGORIES.put("queryProcessTrace", Set.of("流程"));
        TOOL_CATEGORIES.put("queryMyTodoTasks", Set.of("流程"));
        TOOL_CATEGORIES.put("completeApprovalTask", Set.of("流程"));
        TOOL_CATEGORIES.put("queryPerformanceResult", Set.of("绩效"));
        TOOL_CATEGORIES.put("generatePerformanceReport", Set.of("绩效"));
        TOOL_CATEGORIES.put("generateTrainingPlan", Set.of("培训"));
        TOOL_CATEGORIES.put("queryTrainingCourses", Set.of("培训"));
        TOOL_CATEGORIES.put("hrKnowledgeQA", Set.of("知识"));
    }

    /**
     * 根据用户查询，从全部工具中筛选出相关工具规格列表。
     *
     * @param userQuery 用户的原始查询文本
     * @param allTools  全部可用工具规格
     * @return 筛选后的工具规格列表
     */
    public static List<ToolSpecification> filter(String userQuery, List<ToolSpecification> allTools) {
        if (userQuery == null || userQuery.isBlank()) {
            return allTools;
        }

        Set<String> matchedCategories = new HashSet<>();

        // 遍历分类关键词，识别用户意图
        for (Map.Entry<String, Set<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (userQuery.contains(keyword)) {
                    matchedCategories.add(entry.getKey());
                    break; // 同一分类匹配一个关键词即可
                }
            }
        }

        // 安全兜底：未匹配到任何分类时，返回全部工具
        if (matchedCategories.isEmpty()) {
            return allTools;
        }

        // 当匹配到非"员工"分类时，自动附带员工工具（多数操作需查员工信息）
        // 但"员工"分类本身被匹配时无需额外操作
        // 如果匹配的分类不止"员工"，则自动加上"员工"分类
        if (matchedCategories.size() > 1 || !matchedCategories.contains("员工")) {
            matchedCategories.add("员工");
        }

        // 筛选工具
        return allTools.stream()
                .filter(tool -> toolBelongsToAny(tool, matchedCategories))
                .collect(Collectors.toList());
    }

    /**
     * 判断工具是否属于任一匹配分类
     */
    private static boolean toolBelongsToAny(ToolSpecification tool, Set<String> categories) {
        Set<String> toolCats = TOOL_CATEGORIES.get(tool.name());
        if (toolCats == null) {
            return false;
        }
        for (String cat : toolCats) {
            if (categories.contains(cat)) {
                return true;
            }
        }
        return false;
    }
}