package org.example.hragent.agent.nodes;

import org.bsc.langgraph4j.action.NodeAction;
import org.example.hragent.agent.state.HrAgentState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 结果校验节点
 * 校验工具执行后返回的结果
 */
@Component
public class ResultValidationNode implements NodeAction<HrAgentState> {
    
    @Override
    public Map<String, Object> apply(HrAgentState state) {
        try {
            String toolResults = state.toolResults();
            
            if (toolResults == null || toolResults.isEmpty()) {
                return handleValidationFailure(state, "没有可校验的工具结果");
            }
            
            // 校验结果
            ValidationResult validation = validateResult(toolResults);
            
            Map<String, Object> updates = new HashMap<>();
            
            if (validation.isValid()) {
                updates.put(HrAgentState.VALIDATION_STATUS_KEY, "validated");
            } else {
                updates.put(HrAgentState.VALIDATION_STATUS_KEY, "failed");
                updates.put(HrAgentState.ERROR_MESSAGE_KEY, validation.getMessage());
            }
            
            return updates;
            
        } catch (Exception e) {
            return handleValidationFailure(state, "校验过程失败：" + e.getMessage());
        }
    }
    
    private ValidationResult validateResult(String result) {
        // 检查结果是否为 null 或空
        if (result == null || result.trim().isEmpty()) {
            return new ValidationResult(false, "结果为空或 null");
        }
        
        // 检查结果中是否包含错误标识
        if (result.toLowerCase().contains("error") || 
            result.toLowerCase().contains("exception") ||
            result.toLowerCase().contains("failed")) {
            return new ValidationResult(false, "结果中包含错误标识");
        }
        
        // 检查结果是否过短（可能数据不完整）
        if (result.length() < 10) {
            return new ValidationResult(false, "结果过短，可能不完整");
        }
        
        // 基础校验通过
        return new ValidationResult(true, "结果校验通过");
    }
    
    private Map<String, Object> handleValidationFailure(HrAgentState state, String errorMessage) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(HrAgentState.VALIDATION_STATUS_KEY, "failed");
        updates.put(HrAgentState.ERROR_MESSAGE_KEY, errorMessage);
        return updates;
    }
    
    /**
     * 校验结果内部封装类
     */
    private static class ValidationResult {
        private final boolean valid;
        private final String message;
        
        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
    }
}