package org.example.hragent.agent.routing;

import org.example.hragent.agent.state.ToolCallRecord;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 工具规划策略（分层 Tool Routing 中「模型精选 → 生成参数」这一步的可切换实现）。
 * <p>
 * 决定「本轮由谁来选定工具并给出参数」：
 * <ul>
 *   <li>默认实现 {@link NoopToolPlanningStrategy}：不规划，交回远程模型按候选 Schema 自选；</li>
 *   <li>模型实现 {@link LocalModelToolPlanningStrategy}：由本地轻量模型只对<b>只读</b>候选工具
 *       完成选工具 + 生成参数，命中时跳过远程模型的首次推理。</li>
 * </ul>
 * 返回空 {@link Optional}（或空列表）表示「本次不由本地规划」，调用方回退到远程模型自选，
 * 从而保证本地模型能力不足时影响面最小、可一键回退。
 */
public interface ToolPlanningStrategy {

    /**
     * 依据用户请求与候选工具规划工具调用。
     *
     * @param query      用户请求原文
     * @param candidates 本轮路由命中的候选工具卡片（含只读标记与参数 Schema）
     * @param domains    本轮识别到的业务域
     * @return 规划出的工具调用记录；空表示本次不规划、交回远程模型自选
     */
    Optional<List<ToolCallRecord>> plan(String query, List<ToolCard> candidates, Set<String> domains);
}
