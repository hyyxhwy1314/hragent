package org.example.hragent.agent.routing;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.example.hragent.agent.mcp.FeishuMcpToolProvider;
import org.example.hragent.agent.tools.HrToolSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 工具注册表：聚合本地 @Tool 工具与远程 MCP 工具，产出可路由的 {@link ToolCard} 列表。
 * <p>
 * 同时内联维护「工具元数据声明式配置表」（见本文件底部的 {@link Metadata} 与
 * {@link #metadataOf(String)}），集中声明每个工具的业务域、标签、风险等级、命名空间与角色限制。
 * <ul>
 *   <li>本地工具的 Schema 通过反射一次性构建并缓存</li>
 *   <li>MCP 工具按需从 {@link FeishuMcpToolProvider} 读取（该提供者内部已做惰性初始化与失败降级）</li>
 *   <li>元数据缺省时按来源给出安全兜底，保证路由不因缺配置而漏掉工具</li>
 *   <li>新增工具只需在下方配置表登记一行，无需改动工具方法本身</li>
 * </ul>
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    /** MCP 未登记工具的兜底业务域 */
    private static final Set<String> MCP_DEFAULT_DOMAINS = Set.of("飞书");

    /** 判定写操作的工具名关键字（未登记元数据时的风险推断） */
    private static final List<String> WRITE_KEYWORDS = List.of("create", "update", "add", "delete", "remove", "send");

    /** 仅限 HR 相关角色调用的角色集合 */
    private static final Set<String> HR_ROLES = Set.of("HR", "HRBP", "ADMIN");

    private final List<HrToolSet> hrToolSets;
    private final FeishuMcpToolProvider feishuMcpToolProvider;

    /** 本地工具卡片缓存（反射构建一次） */
    private volatile List<ToolCard> localCards;
    /** 工具名 → 承载该 @Tool 方法的 Bean（执行时反射调用） */
    private volatile Map<String, Object> localOwners;

    public ToolRegistry(List<HrToolSet> hrToolSets, FeishuMcpToolProvider feishuMcpToolProvider) {
        this.hrToolSets = hrToolSets;
        this.feishuMcpToolProvider = feishuMcpToolProvider;
    }

    /** 全部工具卡片（本地 + MCP） */
    public List<ToolCard> allCards() {
        Map<String, ToolCard> merged = new LinkedHashMap<>();
        for (ToolCard card : localCards()) {
            merged.put(card.name(), card);
        }
        for (ToolCard card : mcpCards()) {
            merged.putIfAbsent(card.name(), card);
        }
        return List.copyOf(merged.values());
    }

    /** 按名称查询工具卡片 */
    public Optional<ToolCard> cardOf(String toolName) {
        return allCards().stream().filter(card -> card.name().equals(toolName)).findFirst();
    }

    /** 按名称查询承载该工具的本地 Bean（MCP 工具返回空） */
    public Optional<Object> ownerOf(String toolName) {
        ensureLocalLoaded();
        return Optional.ofNullable(localOwners.get(toolName));
    }

    private List<ToolCard> localCards() {
        ensureLocalLoaded();
        return localCards;
    }

    /** 反射构建本地工具卡片与执行归属，构建一次并缓存 */
    private void ensureLocalLoaded() {
        if (localCards != null) {
            return;
        }
        synchronized (this) {
            if (localCards != null) {
                return;
            }
            List<ToolCard> cards = new ArrayList<>();
            Map<String, Object> owners = new HashMap<>();
            for (HrToolSet toolSet : hrToolSets) {
                for (ToolSpecification spec : ToolSpecifications.toolSpecificationsFrom(toolSet)) {
                    cards.add(toCard(spec, ToolCard.Source.LOCAL));
                    owners.put(spec.name(), toolSet);
                }
            }
            localOwners = Map.copyOf(owners);
            localCards = List.copyOf(cards);
            log.info("工具注册表已加载本地工具 {} 个", localCards.size());
        }
    }

    private List<ToolCard> mcpCards() {
        return feishuMcpToolProvider.toolSpecifications().stream()
                .map(spec -> toCard(spec, ToolCard.Source.MCP))
                .toList();
    }

    private ToolCard toCard(ToolSpecification spec, ToolCard.Source source) {
        Metadata meta = metadataOf(spec.name())
                .orElseGet(() -> fallbackMetadata(spec.name(), source));
        return new ToolCard(
                spec.name(),
                meta.namespace(),
                spec.description(),
                meta.domains(),
                meta.tags(),
                meta.riskLevel(),
                meta.allowedRoles(),
                source,
                true,
                spec);
    }

    /**
     * 未登记元数据的工具兜底：给出安全的默认域与风险等级，保证路由不因缺配置而漏掉工具。
     */
    private Metadata fallbackMetadata(String name, ToolCard.Source source) {
        String lower = name.toLowerCase(Locale.ROOT);
        boolean writeLike = WRITE_KEYWORDS.stream().anyMatch(lower::contains);
        ToolCard.RiskLevel risk = writeLike ? ToolCard.RiskLevel.WRITE : ToolCard.RiskLevel.READ;
        Set<String> domains = source == ToolCard.Source.MCP ? MCP_DEFAULT_DOMAINS : Set.of();
        String namespace = source == ToolCard.Source.MCP ? "mcp" : "hr";
        return new Metadata(namespace, domains, Set.of(), risk, Set.of());
    }

    // ==================== 工具元数据声明式配置表 ====================

    /** 工具名 → 声明式元数据 */
    private static final Map<String, Metadata> METADATA_TABLE = new LinkedHashMap<>();

    static {
        // ==================== 员工域 ====================
        read("queryEmployeeInfo", "hr.employee", set("员工"),
                set("员工", "员工信息", "人员", "同事", "工号", "姓名", "档案", "employee"));
        read("queryEmployeeList", "hr.employee", set("员工"),
                set("员工", "员工列表", "花名册", "全部员工", "人员名单", "employee list"));

        // ==================== 岗位域 ====================
        read("queryJobInfo", "hr.job", set("岗位"),
                set("岗位", "职位", "招聘岗位", "岗位详情", "job"));
        read("queryJobList", "hr.job", set("岗位"),
                set("岗位", "职位", "岗位列表", "在招岗位", "job list"));

        // ==================== 简历域 ====================
        read("queryResumeInfo", "hr.resume", set("简历"),
                set("简历", "候选人", "应聘者", "投递", "简历详情"));
        read("queryResumeMatch", "hr.resume", set("简历"),
                set("简历匹配", "匹配分数", "人岗匹配", "匹配度"));
        write("registerCandidate", "hr.resume", set("简历"),
                set("登记简历", "录入候选人", "新增候选人", "简历登记"));
        write("acceptCandidate", "hr.resume", set("简历"),
                set("录用", "录用候选人", "标记录用", "通过候选人"));

        // ==================== 流程域 ====================
        write("startOnboardingProcess", "hr.process", set("简历", "流程"),
                set("入职", "发起入职", "入职流程", "办理入职"));
        write("startResignationProcess", "hr.process", set("流程"),
                set("离职", "发起离职", "离职申请", "办理离职"));
        write("startTransferProcess", "hr.process", set("流程"),
                set("调岗", "发起调岗", "岗位调动", "转岗"));
        write("startRegularProcess", "hr.process", set("流程"),
                set("转正", "发起转正", "试用转正", "转正申请"));
        read("queryProcessInstances", "hr.process", set("流程"),
                set("流程实例", "审批实例", "流程列表", "审批记录"));
        read("queryProcessTrace", "hr.process", set("流程"),
                set("审批轨迹", "流程轨迹", "审批进度", "节点状态"));
        read("queryMyTodoTasks", "hr.process", set("流程"),
                set("待办", "我的待办", "待审批", "待办任务"));
        write("completeApprovalTask", "hr.process", set("流程"),
                set("审批", "通过", "拒绝", "审批意见", "处理待办"));
        read("checkRegularEligibility", "hr.process", set("流程", "自助"),
                set("转正资格", "转正条件", "资格校验", "是否可转正"));
        read("generateApprovalSummary", "hr.process", set("流程"),
                set("审批摘要", "风险提示", "审批建议", "摘要"));
        restricted("inspectStuckFlows", "hr.process", set("流程"),
                set("异常巡检", "卡单", "超时流程", "流程巡检"),
                ToolCard.RiskLevel.READ, HR_ROLES);

        // ==================== 绩效域 ====================
        read("queryPerformanceResult", "hr.performance", set("绩效"),
                set("绩效", "考核", "绩效结果", "评分"));
        write("generatePerformanceReport", "hr.performance", set("绩效"),
                set("绩效报告", "绩效分析", "考核报告"));

        // ==================== 培训域 ====================
        write("generateTrainingPlan", "hr.training", set("培训"),
                set("培训计划", "学习计划", "培训方案"));
        read("queryTrainingCourses", "hr.training", set("培训"),
                set("培训课程", "课程列表", "学习课程", "技能课程"));

        // ==================== 知识域 ====================
        read("hrKnowledgeQA", "hr.knowledge", set("知识"),
                set("政策", "制度", "规定", "规则", "知识", "手册", "HR问答"));

        // ==================== 自助域 ====================
        write("applyLeaveRequest", "hr.selfservice", set("自助", "流程"),
                set("请假", "假期申请", "发起请假", "年假"));
        read("queryLeaveBalance", "hr.selfservice", set("自助"),
                set("假期余额", "年假余额", "可用假期", "余额"));
        read("queryMyLeaveRequests", "hr.selfservice", set("自助"),
                set("我的请假", "请假记录", "假期申请记录"));
        write("applyCertificateRequest", "hr.selfservice", set("自助", "流程"),
                set("证明", "在职证明", "证明申请", "开证明"));
        read("queryExpireReminders", "hr.selfservice", set("自助"),
                set("到期提醒", "合同到期", "证件到期", "提醒"));

        // ==================== 文档域 ====================
        write("generateExcelFile", "hr.document", set("文档"),
                set("excel", "表格", "导出", "花名册", "工资表", "xlsx"));
        write("generateWordDocument", "hr.document", set("文档"),
                set("word", "文档", "通知书", "在职证明", "docx", "录用通知", "离职证明"));

        // ==================== 沟通域 ====================
        write("composeInternalNotice", "hr.communication", set("沟通"),
                set("通知", "公告", "话术", "文案", "邮件", "祝福", "内部通知"));

        // ==================== 飞书云文档域（MCP） ====================
        write("create-doc", "feishu.document", set("飞书", "文档"),
                set("飞书", "云文档", "创建文档", "新建文档"));
        read("fetch-doc", "feishu.document", set("飞书", "文档"),
                set("飞书", "云文档", "读取文档", "获取文档"));
        write("update-doc", "feishu.document", set("飞书", "文档"),
                set("飞书", "云文档", "更新文档", "编辑文档"));
        read("list-docs", "feishu.document", set("飞书", "文档"),
                set("飞书", "云文档", "文档列表", "列出文档"));
        read("get-comments", "feishu.document", set("飞书", "文档"),
                set("飞书", "评论", "获取评论", "文档评论"));
        write("add-comments", "feishu.document", set("飞书", "文档"),
                set("飞书", "评论", "添加评论", "发表评论"));
        read("search-doc", "feishu.document", set("飞书", "知识"),
                set("飞书", "搜索文档", "查找文档"));
        read("get-user", "feishu.user", set("飞书", "员工"),
                set("飞书", "用户", "人员信息", "获取用户"));
        read("fetch-file", "feishu.document", set("飞书", "文档"),
                set("飞书", "文件", "下载文件", "获取文件"));
    }

    /** 工具声明式元数据（不含 Schema），由本表集中维护 */
    public record Metadata(
            String namespace,
            Set<String> domains,
            Set<String> tags,
            ToolCard.RiskLevel riskLevel,
            Set<String> allowedRoles) {

        public Metadata {
            domains = domains == null ? Set.of() : Set.copyOf(domains);
            tags = tags == null ? Set.of() : Set.copyOf(tags);
            allowedRoles = allowedRoles == null ? Set.of() : Set.copyOf(allowedRoles);
        }
    }

    /** 按工具名查询元数据 */
    public static Optional<Metadata> metadataOf(String toolName) {
        return Optional.ofNullable(METADATA_TABLE.get(toolName));
    }

    private static void read(String name, String namespace, Set<String> domains, Set<String> tags) {
        METADATA_TABLE.put(name, new Metadata(namespace, domains, tags, ToolCard.RiskLevel.READ, Set.of()));
    }

    private static void write(String name, String namespace, Set<String> domains, Set<String> tags) {
        METADATA_TABLE.put(name, new Metadata(namespace, domains, tags, ToolCard.RiskLevel.WRITE, Set.of()));
    }

    private static void restricted(String name, String namespace, Set<String> domains, Set<String> tags,
                                   ToolCard.RiskLevel riskLevel, Set<String> roles) {
        METADATA_TABLE.put(name, new Metadata(namespace, domains, tags, riskLevel, roles));
    }

    private static Set<String> set(String... values) {
        return Set.of(values);
    }
}
