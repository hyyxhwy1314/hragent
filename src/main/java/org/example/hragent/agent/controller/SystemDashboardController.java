package org.example.hragent.agent.controller;

import org.example.hragent.mapper.agent.AgentInteractLogMapper;
import org.example.hragent.mapper.system.BehaviorLogMapper;
import org.example.hragent.mapper.system.SystemDashboardMapper;
import org.example.hragent.vo.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final BehaviorLogMapper behaviorLogMapper;

    public SystemDashboardController(AgentInteractLogMapper interactLogMapper,
                                      SystemDashboardMapper systemDashboardMapper,
                                      BehaviorLogMapper behaviorLogMapper) {
        this.interactLogMapper = interactLogMapper;
        this.systemDashboardMapper = systemDashboardMapper;
        this.behaviorLogMapper = behaviorLogMapper;
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
     * <p>
     * 融合 行为日志（全量操作）/ AI 交互日志（含消息原文）/ 流程实例日志（含申请人），
     * 按时间倒序去重后返回，构成完整操作时间线。
     */
    @GetMapping("/recent-activity")
    public R<List<Map<String, Object>>> recentActivity(@RequestParam(required = false) Integer limit) {
        if (limit == null || limit < 1) limit = 20;
        List<Map<String, Object>> logs = new ArrayList<>();

        // 1. 行为日志（基础时间线，覆盖所有操作/流程/AI 交互）
        // SQL 层已排除纯 GET 查询（页面加载/列表读取不构成"操作"），避免时间线被"查询"刷屏
        List<Map<String, Object>> behaviorLogs = behaviorLogMapper.statRecentActivity(limit);
        for (Map<String, Object> log : behaviorLogs) {
            String module = String.valueOf(log.getOrDefault("module", ""));
            String action = String.valueOf(log.getOrDefault("action", ""));
            // AI 交互由交互日志补充原文；流程发起由实例日志补充申请人，避免重复
            if ("AI交互".equals(module) || ("流程审批".equals(module) && "发起流程".equals(action))) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", log.get("createTime"));
            item.put("type", "流程审批".equals(module) ? "flow" : "op");
            item.put("module", module);
            item.put("action", action);
            item.put("summary", log.get("summary"));
            item.put("detail", log.get("detail"));
            item.put("status", log.get("status"));
            item.put("tokenCount", log.get("tokenCount"));
            item.put("username", log.get("username"));
            logs.add(item);
        }

        // 2. AI 交互日志（补充用户消息原文）
        List<Map<String, Object>> aiLogs = systemDashboardMapper.statRecentAiLogs(limit);
        for (Map<String, Object> log : aiLogs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", log.get("createTime"));
            item.put("type", "ai");
            item.put("module", "AI交互");
            item.put("action", "智能问答");
            item.put("summary", log.get("userMessage"));
            item.put("detail", log.get("aiAnswer"));
            item.put("status", "success");
            item.put("tokenCount", toLong(log.get("inputTokens")) + toLong(log.get("outputTokens")));
            logs.add(item);
        }

        // 3. 流程实例日志（补充申请人信息）
        List<Map<String, Object>> flowLogs = systemDashboardMapper.statRecentFlowLogs(limit);
        for (Map<String, Object> log : flowLogs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", log.get("createTime"));
            item.put("type", "flow");
            item.put("module", "流程审批");
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
            String applicantName = String.valueOf(log.getOrDefault("applicantName", ""));
            if (applicantName.isEmpty() || "null".equalsIgnoreCase(applicantName)) {
                applicantName = "";
            }
            item.put("username", applicantName);
            logs.add(item);
        }

        // 按时间倒序 + 按 时间|类型|动作|摘要 去重（同一秒内的完全重复视为同一条）
        Set<String> seen = new HashSet<>();
        logs.sort((a, b) -> String.valueOf(b.getOrDefault("time", ""))
                .compareTo(String.valueOf(a.getOrDefault("time", ""))));
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> log : logs) {
            String key = String.join("|",
                    String.valueOf(log.getOrDefault("time", "")),
                    String.valueOf(log.getOrDefault("type", "")),
                    String.valueOf(log.getOrDefault("action", "")),
                    String.valueOf(log.getOrDefault("summary", "")));
            if (seen.add(key)) {
                merged.add(log);
            }
            if (merged.size() >= limit) break;
        }

        return R.ok(merged);
    }

    /**
     * Token 消耗看板汇总
     * <p>
     * 今日/周期消耗、输入输出拆分、日均、峰值日及峰值 Token
     */
    @GetMapping("/token-summary")
    public R<Map<String, Object>> tokenSummary(@RequestParam(required = false) Integer days) {
        if (days == null || days < 1) days = 30;
        Map<String, Object> stat = behaviorLogMapper.statTokenSummary(days);
        Map<String, Object> peak = behaviorLogMapper.statTokenPeak(days);
        if (peak != null) {
            stat.putAll(peak);
        } else {
            stat.put("peakDay", "-");
            stat.put("peakValue", 0);
        }
        return R.ok(stat);
    }

    /**
     * Token 按天趋势（输入/输出拆分）
     */
    @GetMapping("/token-trend")
    public R<List<Map<String, Object>>> tokenTrend(@RequestParam(required = false) Integer days) {
        return R.ok(behaviorLogMapper.statTokenDaily(days));
    }

    /**
     * 行为日志按模块统计（数量与 Token）
     */
    @GetMapping("/behavior-module")
    public R<List<Map<String, Object>>> behaviorModule(@RequestParam(required = false) Integer days) {
        return R.ok(behaviorLogMapper.statBehaviorModule(days));
    }

    /**
     * 行为日志按天统计
     */
    @GetMapping("/behavior-daily")
    public R<List<Map<String, Object>>> behaviorDaily(@RequestParam(required = false) Integer days) {
        return R.ok(behaviorLogMapper.statBehaviorDaily(days));
    }

    /**
     * 操作日志分页查询（按 模块/关键词/状态 过滤，时间倒序）
     */
    @GetMapping("/behavior-logs")
    public R<Map<String, Object>> behaviorLogs(@RequestParam(required = false) String module,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1 || size > 100) size = 20;
        int offset = (page - 1) * size;
        List<Map<String, Object>> list = behaviorLogMapper.statBehaviorLogs(module, keyword, status, offset, size);
        long total = behaviorLogMapper.countBehaviorLogs(module, keyword, status);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return R.ok(result);
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