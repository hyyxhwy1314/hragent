package org.example.hragent.agent.nodes;

import org.bsc.langgraph4j.action.NodeAction;
import org.example.hragent.agent.state.HrAgentState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 答案生成节点
 * <p>
 * 在 LangChain4j 架构中，ToolInvocationNode 已经通过 AiService 获得了最终回答，
 * 本节点仅做最终确认和格式化：
 * <ul>
 *     <li>正常情况：透传工具结果作为最终回答</li>
 *     <li>异常情况：格式化错误信息返回给用户</li>
 * </ul>
 */
@Component
public class AnswerGenerationNode implements NodeAction<HrAgentState> {
    
    @Override
    public Map<String, Object> apply(HrAgentState state) {
        // 如果发生错误，生成友好的错误提示
        if (state.hasError()) {
            String errorResponse = String.format(
                "抱歉，处理您的请求时遇到错误：%s。请重试或联系技术支持。",
                state.errorMessage()
            );
            Map<String, Object> updates = new HashMap<>();
            updates.put(HrAgentState.MESSAGES_KEY, HrAgentState.ASSISTANT_PREFIX + errorResponse);
            return updates;
        }
        
        // 正常情况：ToolInvocationNode 已将 AI 回答写入 MESSAGES_KEY，无需重复添加
        // 返回空 Map 表示不修改状态
        return Map.of();
    }
}
