package org.example.hragent.agent.controller;

import org.example.hragent.agent.graph.AgentScheduler;
import org.example.hragent.agent.persistence.AgentStatePersistenceService;
import org.example.hragent.annotation.RateLimit;
import org.example.hragent.annotation.RepeatSubmit;
import org.example.hragent.entity.agent.AgentMessage;
import org.example.hragent.entity.agent.AgentSession;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.vo.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Agent 控制器
 * 提供 HR Agent 交互的 REST API 接口
 */
@RestController
@RequestMapping("/agent")
public class AgentController {
    
    private final AgentScheduler agentScheduler;
    private final AgentStatePersistenceService persistenceService;
    
    public AgentController(AgentScheduler agentScheduler,
                          AgentStatePersistenceService persistenceService) {
        this.agentScheduler = agentScheduler;
        this.persistenceService = persistenceService;
    }
    
    /**
     * 处理单条消息
     * AI 对话调用 LLM 成本高、耗时长，需限流 + 防重复提交
     */
    @PostMapping("/chat")
    @RateLimit(rate = 5, rateInterval = 10, rateIntervalUnit = TimeUnit.SECONDS, message = "提问太频繁，请稍后再试")
    @RepeatSubmit(interval = 3, unit = TimeUnit.SECONDS, message = "消息正在处理，请勿重复发送")
    public R<ChatResponse> chat(@RequestBody AgentRequest request) {
        ChatResponse response = agentScheduler.processMessageWithSession(
            request.getMessage(),
            String.valueOf(CurrentUserService.empId())
        );
        return R.ok(response);
    }
    
    /**
     * 继续已有会话
     * AI 对话调用 LLM 成本高、耗时长，需限流 + 防重复提交
     */
    @PostMapping("/chat/{sessionId}")
    @RateLimit(rate = 5, rateInterval = 10, rateIntervalUnit = TimeUnit.SECONDS, message = "提问太频繁，请稍后再试")
    @RepeatSubmit(interval = 3, unit = TimeUnit.SECONDS, message = "消息正在处理，请勿重复发送")
    public R<ChatResponse> continueChat(@PathVariable String sessionId, @RequestBody AgentRequest request) {
        ChatResponse response = agentScheduler.continueConversation(request.getMessage(), sessionId);
        return R.ok(response);
    }

    /**
     * 清除指定会话
     */
    @DeleteMapping("/session/{sessionId}")
    public R<Boolean> clearSession(@PathVariable String sessionId) {
        agentScheduler.clearSession(sessionId);
        return R.ok(true);
    }
    
    /**
     * 获取当前用户的会话列表
     */
    @GetMapping("/sessions")
    public R<List<AgentSession>> getSessions() {
        Long userId = CurrentUserService.empId();
        if (userId == null) {
            return R.ok(List.of());
        }
        List<AgentSession> sessions = persistenceService.getUserSessions(userId);
        return R.ok(sessions);
    }
    
    /**
     * 获取指定会话的消息列表
     */
    @GetMapping("/session/{sessionId}/messages")
    public R<List<AgentMessage>> getSessionMessages(@PathVariable String sessionId) {
        List<AgentMessage> messages = persistenceService.getSessionMessages(sessionId);
        return R.ok(messages);
    }
    
    /**
     * Agent 对话请求 DTO
     */
    public static class AgentRequest {
        private String message;
        private String userId;
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }
    
    /**
     * Agent 对话响应 DTO
     * 包含完整交互轨迹：思考过程 + 工具调用 + 最终回答，便于前端按主流 Agent 风格展示
     */
    public static class ChatResponse {
        private String response;           // 最终回答（支持 Markdown）
        private String thinking;           // 完整思考过程（每一轮推理+工具调用）
        private List<ToolCallStep> toolSteps; // 工具调用步骤记录
        private String sessionId;
        private int inputTokens;           // 本回合消耗的输入 Token 数
        private int outputTokens;          // 本回合消耗的输出 Token 数
        
        public ChatResponse(String response, String sessionId) {
            this.response = response;
            this.sessionId = sessionId;
            this.thinking = null;
            this.toolSteps = null;
            this.inputTokens = 0;
            this.outputTokens = 0;
        }
        
        public ChatResponse(String response, String thinking, List<ToolCallStep> toolSteps, String sessionId, int inputTokens, int outputTokens) {
            this.response = response;
            this.thinking = thinking;
            this.toolSteps = toolSteps;
            this.sessionId = sessionId;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
        }
        
        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }
        public String getThinking() { return thinking; }
        public void setThinking(String thinking) { this.thinking = thinking; }
        public List<ToolCallStep> getToolSteps() { return toolSteps; }
        public void setToolSteps(List<ToolCallStep> toolSteps) { this.toolSteps = toolSteps; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public int getInputTokens() { return inputTokens; }
        public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }
        public int getOutputTokens() { return outputTokens; }
        public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
        
        /**
         * 单步工具调用记录
         */
        public static class ToolCallStep {
            private String toolName;
            private String arguments;
            private String result;
            private String timestamp;
            
            public ToolCallStep(String toolName, String arguments, String result, String timestamp) {
                this.toolName = toolName;
                this.arguments = arguments;
                this.result = result;
                this.timestamp = timestamp;
            }
            
            public String getToolName() { return toolName; }
            public String getArguments() { return arguments; }
            public String getResult() { return result; }
            public String getTimestamp() { return timestamp; }
        }
    }
}