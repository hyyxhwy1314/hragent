package org.example.hragent.agent.routing;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 工具粗分类器（分层 Tool Routing 第 3 层）。
 * <p>
 * 用规则把用户请求映射到业务域，缩小后续 BM25 召回的候选范围；
 * 偏向高召回——命中多个域时全部保留，未命中任何域时返回空集，
 * 由路由层跳过域收缩、直接在可见工具全集中召回。
 * <p>
 * 规则以有序列表声明，避免层层嵌套的条件分支。
 */
@Component
public class ToolClassifier {

    /** 业务域 → 触发关键词 */
    private record DomainRule(String domain, Set<String> keywords) {
    }

    private static final List<DomainRule> RULES = List.of(
            new DomainRule("员工", Set.of("员工", "雇员", "人员", "同事", "工号")),
            new DomainRule("简历", Set.of("简历", "候选人", "登记", "录用", "面试", "投递", "匹配")),
            new DomainRule("流程", Set.of("流程", "入职", "离职", "调岗", "转正", "审批", "待办")),
            new DomainRule("岗位", Set.of("岗位", "职位", "招聘", "招人", "发布")),
            new DomainRule("绩效", Set.of("绩效", "考核", "评分", "报告")),
            new DomainRule("培训", Set.of("培训", "课程", "学习", "技能")),
            new DomainRule("知识", Set.of("政策", "制度", "规定", "规则", "知识", "手册", "规章")),
            new DomainRule("自助", Set.of("请假", "假期", "年假", "余额", "证明", "合同", "到期", "提醒")),
            new DomainRule("文档", Set.of("excel", "表格", "导出", "花名册", "工资表", "xlsx",
                    "word", "文档", "通知书", "docx", "离职证明")),
            new DomainRule("飞书", Set.of("飞书", "云文档", "知识库", "评论")),
            new DomainRule("沟通", Set.of("通知", "公告", "话术", "文案", "邮件", "祝福")));

    /**
     * 识别请求命中的业务域集合。
     *
     * @return 命中的业务域；未命中任何规则时返回空集（表示「不确定」，交由召回层处理）
     */
    public Set<String> classify(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        Set<String> matched = new LinkedHashSet<>();
        for (DomainRule rule : RULES) {
            if (hitsAny(query, rule.keywords())) {
                matched.add(rule.domain());
            }
        }
        return matched;
    }

    private boolean hitsAny(String query, Set<String> keywords) {
        for (String keyword : keywords) {
            if (query.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}