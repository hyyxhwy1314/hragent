package org.example.hragent.agent.controller;

import org.example.hragent.mapper.agent.AgentInteractLogMapper;
import org.example.hragent.vo.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 交互数据看板接口
 * <p>
 * 提供对话量、Token 消耗、耗时、工具调用分布等聚合统计，
 * 供前端 AI 数据看板页面展示。聚合查询优先走业务库（MySQL），
 * 大数据链路（CDC->Kafka->Flink->ClickHouse）提供实时/离线增强。
 */
@RestController
@RequestMapping("/agent/dashboard")
public class AiDashboardController {

    private final AgentInteractLogMapper interactLogMapper;

    public AiDashboardController(AgentInteractLogMapper interactLogMapper) {
        this.interactLogMapper = interactLogMapper;
    }

    /**
     * 汇总指标卡片（总对话、工具调用、Token、平均耗时、错误数）
     */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(@RequestParam(required = false) Integer days) {
        Map<String, Object> summary = interactLogMapper.statSummary(days);
        if (summary == null) {
            summary = new HashMap<>();
        }
        return R.ok(summary);
    }

    /**
     * 按天统计对话量与 Token 消耗趋势
     */
    @GetMapping("/daily")
    public R<List<Map<String, Object>>> daily(@RequestParam(required = false) Integer days) {
        return R.ok(interactLogMapper.statDaily(days));
    }

    /**
     * 工具调用分布（近 N 天 top 工具）
     */
    @GetMapping("/tool-distribution")
    public R<List<Map<String, Object>>> toolDistribution(@RequestParam(required = false) Integer days) {
        return R.ok(interactLogMapper.statToolDistribution(days));
    }
}