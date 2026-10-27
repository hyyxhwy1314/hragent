package org.example.hragent.agent.routing;

import org.example.hragent.agent.state.ToolCallRecord;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 默认工具规划策略：不做任何规划（分层 Tool Routing 第 4 层「模型精选」由远程模型承担）。
 * <p>
 * 始终返回空结果，使 {@link org.example.hragent.agent.nodes.ModelThinkNode} 走原有远程模型路径，
 * 从而在未启用本地规划时行为与启用前完全一致。
 */
@Component
public class NoopToolPlanningStrategy implements ToolPlanningStrategy {

    @Override
    public Optional<List<ToolCallRecord>> plan(String query, List<ToolCard> candidates, Set<String> domains) {
        return Optional.empty();
    }
}
