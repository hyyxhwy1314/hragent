package org.example.hragent.agent.controller;

import org.example.hragent.agent.graph.AgentScheduler;
import org.example.hragent.agent.persistence.AgentStatePersistenceService;
import org.example.hragent.entity.agent.AgentMessage;
import org.example.hragent.entity.agent.AgentSession;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.vo.R;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     */
    @PostMapping("/chat")
    public R<ChatResponse> chat(@RequestBody AgentRequest request) {
        AgentScheduler.AgentResponse response = agentScheduler.processMessageWithSession(
            request.getMessage(), 
            String.valueOf(CurrentUserService.empId())
        );
        return R.ok(new ChatResponse(response.getResponse(), response.getSessionId()));
    }
    
    /**
     * 继续已有会话
     */
    @PostMapping("/chat/{sessionId}")
    public R<ChatResponse> continueChat(@PathVariable String sessionId, @RequestBody AgentRequest request) {
        String response = agentScheduler.continueConversation(request.getMessage(), sessionId);
        return R.ok(new ChatResponse(response, sessionId));
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
     */
    public static class ChatResponse {
        private String response;
        private String sessionId;
        
        public ChatResponse(String response, String sessionId) {
            this.response = response;
            this.sessionId = sessionId;
        }
        
        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }
}