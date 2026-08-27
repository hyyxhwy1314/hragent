package org.example.hragent.agent.nodes;

import org.bsc.langgraph4j.action.NodeAction;
import org.example.hragent.agent.state.AgentIntent;
import org.example.hragent.agent.state.HrAgentState;
import org.example.hragent.agent.tools.HrBusinessTools;
import org.example.hragent.agent.tools.ToolOrchestrationService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用节点
 * 根据识别出的意图执行业务工具
 */
@Component
public class ToolInvocationNode implements NodeAction<HrAgentState> {
    
    private final HrBusinessTools hrBusinessTools;
    private final ToolOrchestrationService toolOrchestrationService;
    
    public ToolInvocationNode(HrBusinessTools hrBusinessTools,
                              ToolOrchestrationService toolOrchestrationService) {
        this.hrBusinessTools = hrBusinessTools;
        this.toolOrchestrationService = toolOrchestrationService;
    }
    
    @Override
    public Map<String, Object> apply(HrAgentState state) {
        try {
            String intentCode = state.intent();
            if (intentCode == null) {
                return handleToolError(state, "状态中未找到意图");
            }
            
            AgentIntent intent = AgentIntent.fromCode(intentCode);
            
            // 获取最后一条用户消息作为上下文（从后往前找）
            List<String> messages = state.messages();
            String userQuery = "";
            for (int i = messages.size() - 1; i >= 0; i--) {
                String msg = messages.get(i);
                if (msg.startsWith(HrAgentState.USER_PREFIX)) {
                    userQuery = msg.substring(HrAgentState.USER_PREFIX.length());
                    break;
                }
            }
            
            // 通过编排服务进行多工具调用（串行/并行）
            List<String> toolResults = toolOrchestrationService.executeTools(intent, userQuery, state);
            
            Map<String, Object> updates = new HashMap<>();
            
            // 为简化处理，将所有工具结果合并为一条结果
            String combinedResult = String.join("\n\n", toolResults);
            updates.put(HrAgentState.TOOL_RESULTS_KEY, combinedResult);
            
            return updates;
            
        } catch (Exception e) {
            return handleToolError(state, "工具执行失败：" + e.getMessage());
        }
    }
    
    private Map<String, Object> handleToolError(HrAgentState state, String errorMessage) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(HrAgentState.ERROR_MESSAGE_KEY, errorMessage);
        updates.put(HrAgentState.TOOL_RESULTS_KEY, "错误：" + errorMessage);
        return updates;
    }
}