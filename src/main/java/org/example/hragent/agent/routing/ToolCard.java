package org.example.hragent.agent.routing;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.Set;

/**
 * 可路由的工具「卡片」——分层 Tool Routing 的最小注册单元。
 * <p>
 * 除 LangChain4j 的 {@link ToolSpecification}（名称/描述/参数 Schema）外，
 * 额外携带路由所需的元数据：命名空间、业务域、标签、风险等级、可用角色、来源、状态。
 * 工具来源 {@link Source} 与风险等级 {@link RiskLevel} 仅作为本卡片字段的取值而存在，
 * 故作为嵌套类型内联在此，避免为零散枚举单开文件。
 * 清晰的边界定义（描述何时用/何时不用、收紧参数 Schema）是路由质量的上限。
 *
 * @param name           工具唯一名称（本地 camelCase、MCP kebab-case）
 * @param namespace      命名空间，如 hr.employee / feishu.document，用于索引与去重
 * @param description    功能描述（与 ToolSpecification 保持一致）
 * @param domains        归属业务域（可多个），与粗分类结果对齐
 * @param tags           检索标签（含同义词），提升 BM25 召回
 * @param riskLevel      风险等级
 * @param allowedRoles   允许调用的角色集合；为空表示不限制
 * @param source         工具来源（本地 / MCP）
 * @param enabled        是否启用（可热关闭）
 * @param specification  LangChain4j 工具规格（绑定给模型时使用）
 */
public record ToolCard(
        String name,
        String namespace,
        String description,
        Set<String> domains,
        Set<String> tags,
        RiskLevel riskLevel,
        Set<String> allowedRoles,
        Source source,
        boolean enabled,
        ToolSpecification specification) {

    public ToolCard {
        domains = domains == null ? Set.of() : Set.copyOf(domains);
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        allowedRoles = allowedRoles == null ? Set.of() : Set.copyOf(allowedRoles);
    }

    /** 是否对指定角色可见：未声明角色限制时对所有角色可见 */
    public boolean visibleTo(String role) {
        if (allowedRoles.isEmpty()) {
            return true;
        }
        return role != null && allowedRoles.contains(role);
    }

    /** 与目标业务域集合是否有交集（目标为空表示不限域） */
    public boolean inDomains(Set<String> targets) {
        if (targets == null || targets.isEmpty()) {
            return true;
        }
        return domains.stream().anyMatch(targets::contains);
    }

    /** 是否为只读工具 */
    public boolean readOnly() {
        return riskLevel == RiskLevel.READ;
    }

    /**
     * 工具来源：本地 @Tool 工具 或 远程 MCP 工具。
     * 供路由与执行层区分调用通道。
     */
    public enum Source {
        LOCAL,
        MCP
    }

    /**
     * 工具风险等级。
     * <p>
     * 用于分层 Tool Routing 的确定性过滤与执行前风险策略：
     * <ul>
     *   <li>{@link #READ} —— 只读查询，风险最低，可常驻（pinned）或预召回</li>
     *   <li>{@link #WRITE} —— 产生业务副作用的写操作，执行前需参数与权限校验</li>
     *   <li>{@link #HIGH_RISK} —— 不可逆或影响面大的操作，需更严格的候选集与权限校验</li>
     * </ul>
     */
    public enum RiskLevel {
        READ,
        WRITE,
        HIGH_RISK
    }
}
