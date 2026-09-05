package org.example.hragent.agent.controller;

import org.example.hragent.mapper.agent.AgentInteractLogMapper;
import org.example.hragent.mapper.system.SystemDashboardMapper;
import org.example.hragent.vo.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统数据看板接口
 * <p>
 * 聚合展示整个系统的运行状况，包括：
 * <ul>
 *   <li>AI 智能助手交互数据（对话量、Token、工具调用）</li>
 *   <li>HR 业务操作数据（员工、简历、流程、岗位、绩效、培训）</li>
 *   <li>招聘漏斗与流程审批分布</li>
 *   <li>每日业务趋势</li>
 * </ul>
 * 体现大数据价值——从海量系统日志中提取洞察，而非仅展示 AI 指标。
 */
@RestController
@RequestMapping("/dashboard/system")
public class SystemDashboardController {

    private final AgentInteractLogMapper interactLogMapper;
    private final SystemDashboardMapper systemDashboardMapper;

    public SystemDashboardController(AgentInteractLogMapper interactLogMapper,
                                      SystemDashboardMapper systemDashboardMapper) {
        this.interactLogMapper = interactLogMapper;
        this.systemDashboardMapper = systemDashboardMapper;
    }

    /**
     * 系统总体概览指标
     * 包含 AI 交互 + 各业务模块的汇总数据
     */
    @GetMapping("/summary")
    public R<Map<String, Object>> systemSummary(@RequestParam(required = false) Integer days) {
        // AI 交互统计
        Map<String, Object> aiSummary = interactLogMapper.statSummary(days);
        // 系统业务统计
        Map<String, Object> sysSummary = systemDashboardMapper.statSystemSummary(days);
        // 合并
        Map<String, Object> result = new HashMap<>();
        if (aiSummary != null) result.putAll(aiSummary);
        if (sysSummary != null) result.putAll(sysSummary);
        // 计算留存率等衍生指标
        if (result.get("totalCount") != null) {
            long aiCount = toLong(result.get("totalCount"));
            result.put("aiTotalCount", aiCount);
        }
        if (result.get("totalTokens") != null) {
            result.put("totalTokens", result.get("totalTokens"));
        }
        if (result.get("totalToolCalls") != null) {
            result.put("totalToolCalls", result.get("totalToolCalls"));
        }
        if (result.get("avgDurationMs") != null) {
            result.put("avgDurationMs", result.get("avgDurationMs"));
        }
        if (result.get("errorCount") != null) {
            result.put("errorCount", result.get("errorCount"));
        }
        return R.ok(result);
    }

    /**
     * AI 交互按天趋势（对话量 + Token 消耗）
     */
    @GetMapping("/ai-daily")
    public R<List<Map<String, Object>>> aiDaily(@RequestParam(required = false) Integer days) {
        return R.ok(interactLogMapper.statDaily(days));
    }

    /**
     * 工具调用分布 Top10
     */
    @GetMapping("/tool-distribution")
    public R<List<Map<String, Object>>> toolDistribution(@RequestParam(required = false) Integer days) {
        return R.ok(interactLogMapper.statToolDistribution(days));
    }

    /**
     * 业务操作按天趋势
     */
    @GetMapping("/business-daily")
    public R<List<Map<String, Object>>> businessDaily(@RequestParam(required = false) Integer days) {
        return R.ok(systemDashboardMapper.statDailyBusiness(days));
    }

    /**
     * 流程审批类型分布
     */
    @GetMapping("/flow-distribution")
    public R<List<Map<String, Object>>> flowDistribution(@RequestParam(required = false) Integer days) {
        return R.ok(systemDashboardMapper.statFlowDistribution(days));
    }

    /**
     * 招聘漏斗数据
     */
    @GetMapping("/recruitment-funnel")
    public R<List<Map<String, Object>>> recruitmentFunnel() {
        return R.ok(systemDashboardMapper.statRecruitmentFunnel());
    }

    /**
     * 系统最近活动日志
     * 从各业务表中提取最近的操作记录，按时间倒序排列
     */
    @GetMapping("/recent-activity")
    public R<List<Map<String, Object>>> recentActivity(@RequestParam(required = false) Integer limit) {
        if (limit == null || limit < 1) limit = 20;
        List<Map<String, Object>> logs = new ArrayList<>();

        // 1. AI 交互日志
        List<Map<String, Object>> aiLogs = systemDashboardMapper.statRecentAiLogs(limit);
        for (Map<String, Object> log : aiLogs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", log.get("createTime"));
            item.put("type", "ai");
            item.put("action", "智能问答");
            item.put("summary", log.get("userMessage"));
            item.put("detail", log.get("aiAnswer"));
            item.put("status", "success");
            // Token 消耗数
            long inputTokens = toLong(log.get("inputTokens"));
            long outputTokens = toLong(log.get("outputTokens"));
            item.put("tokenCount", inputTokens + outputTokens);
            logs.add(item);
        }

        // 2. 流程审批日志
        List<Map<String, Object>> flowLogs = systemDashboardMapper.statRecentFlowLogs(limit);
        for (Map<String, Object> log : flowLogs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", log.get("createTime"));
            item.put("type", "flow");
            String flowType = String.valueOf(log.getOrDefault("flowType", ""));
            String flowNo = String.valueOf(log.getOrDefault("flowNo", ""));
            Object flowStatus = log.get("flowStatus");
            switch (flowType) {
                case "ONBOARD": item.put("action", "入职申请"); break;
                case "LEAVE": item.put("action", "离职申请"); break;
                case "TRANSFER": item.put("action", "调岗申请"); break;
                case "REGULAR": item.put("action", "转正申请"); break;
                default: item.put("action", "流程申请"); break;
            }
            item.put("summary", flowNo);
            // 流程状态: 0-待审批 1-通过 2-拒绝 3-已取消
            String statusText = "待审批";
            if (flowStatus instanceof Number) {
                int s = ((Number) flowStatus).intValue();
                if (s == 1) statusText = "已通过";
                else if (s == 2) statusText = "已拒绝";
                else if (s == 3) statusText = "已取消";
            }
            item.put("detail", statusText);
            item.put("status", "success");
            // 申请人信息
            String applicantName = String.valueOf(log.getOrDefault("applicantName", ""));
            if (!applicantName.isEmpty() && !"null".equalsIgnoreCase(applicantName)) {
                item.put("applicant", applicantName);
            } else {
                item.put("applicant", "");
            }
            logs.add(item);
        }

        // 按时间倒序排列
        logs.sort((a, b) -> {
            String ta = String.valueOf(a.getOrDefault("time", ""));
            String tb = String.valueOf(b.getOrDefault("time", ""));
            return tb.compareTo(ta);
        });

        // 截取前 limit 条
        if (logs.size() > limit) {
            logs = logs.subList(0, limit);
        }

        return R.ok(logs);
    }

    private long toLong(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}