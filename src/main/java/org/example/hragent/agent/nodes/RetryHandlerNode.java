package org.example.hragent.agent.nodes;

import org.bsc.langgraph4j.action.NodeAction;
import org.example.hragent.agent.state.HrAgentState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 重试处理节点
 * 通过递增重试次数为工具重新执行做准备
 */
@Component
public class RetryHandlerNode implements NodeAction<HrAgentState> {
    
    @Override
    public Map<String, Object> apply(HrAgentState state) {
        int currentRetryCount = state.retryCount();
        int newRetryCount = currentRetryCount + 1;
        
        Map<String, Object> updates = new HashMap<>();
        updates.put(HrAgentState.RETRY_COUNT_KEY, newRetryCount);
        
        // 清除上一次错误以便重试
        updates.put(HrAgentState.ERROR_MESSAGE_KEY, null);
        updates.put(HrAgentState.VALIDATION_STATUS_KEY, "pending");
        
        return updates;
    }
}