package org.example.hragent.agent.nodes;

import org.bsc.langgraph4j.action.NodeAction;
import org.example.hragent.agent.state.HrAgentState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 答案生成节点
 * 基于工具执行结果，生成最终面向用户的响应
 * 不再调用大模型，直接基于工具结果格式化回复，避免 LLM 调用失败
 */
@Component
public class AnswerGenerationNode implements NodeAction<HrAgentState> {
    
    @Override
    public Map<String, Object> apply(HrAgentState state) {
        try {
            // 获取工具结果
            String toolResults = state.toolResults();
            String resultsSummary = (toolResults == null || toolResults.isEmpty()) 
                ? null : toolResults;
            
            // 如果发生错误，返回错误信息
            if (state.hasError()) {
                return handleErrorResponse(state, state.errorMessage());
            }
            
            // 使用工具结果直接作为回答
            String answer;
            if (resultsSummary != null) {
                answer = resultsSummary;
            } else {
                // 无工具结果时，获取最后一条用户消息作为上下文
                List<String> messages = state.messages();
                String userQuery = "";
                for (String msg : messages) {
                    if (msg.startsWith(HrAgentState.USER_PREFIX)) {
                        userQuery = msg.substring(HrAgentState.USER_PREFIX.length());
                    }
                }
                answer = "已收到您的请求：" + userQuery + "，正在为您处理，请稍候。";
            }
            
            Map<String, Object> updates = new HashMap<>();
            updates.put(HrAgentState.MESSAGES_KEY, HrAgentState.ASSISTANT_PREFIX + answer);
            
            return updates;
            
        } catch (Exception e) {
            return handleErrorResponse(state, "答案生成失败：" + e.getMessage());
        }
    }
    
    private Map<String, Object> handleErrorResponse(HrAgentState state, String errorMessage) {
        String errorResponse = String.format(
            "抱歉，处理您的请求时遇到错误：%s。请重试或联系技术支持。",
            errorMessage
        );
        
        Map<String, Object> updates = new HashMap<>();
        updates.put(HrAgentState.MESSAGES_KEY, HrAgentState.ASSISTANT_PREFIX + errorResponse);
        
        return updates;
    }
}