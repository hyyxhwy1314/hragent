package org.example.hragent.agent.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.hragent.agent.state.HrAgentState;
import org.example.hragent.entity.agent.AgentMessage;
import org.example.hragent.entity.agent.AgentSession;
import org.example.hragent.entity.agent.AgentToolLog;
import org.example.hragent.mapper.agent.AgentMessageMapper;
import org.example.hragent.mapper.agent.AgentSessionMapper;
import org.example.hragent.mapper.agent.AgentToolLogMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Agent 状态持久化服务
 * 基于 Redis 进行快速状态存取（适配 LangGraph 状态克隆需求），
 * 同时持久化到 MySQL 存储会话元数据、对话历史和工具调用日志
 */
@Service
public class AgentStatePersistenceService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentToolLogMapper toolLogMapper;
    
    private static final String AGENT_STATE_PREFIX = "agent:state:";
    private static final long DEFAULT_TTL_HOURS = 24;
    
    public AgentStatePersistenceService(RedisTemplate<String, String> redisTemplate,
                                       ObjectMapper objectMapper,
                                       AgentSessionMapper sessionMapper,
                                       AgentMessageMapper messageMapper,
                                       AgentToolLogMapper toolLogMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.toolLogMapper = toolLogMapper;
    }
    
    // ==================== Redis 快速状态存取（LangGraph 工作流使用） ====================
    
    /**
     * 将 Agent 状态保存到 Redis
     */
    public void saveState(String sessionId, HrAgentState state) {
        try {
            String key = buildKey(sessionId);
            String json = objectMapper.writeValueAsString(state.data());
            redisTemplate.opsForValue().set(key, json, DEFAULT_TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Agent 状态序列化失败", e);
        }
    }
    
    /**
     * 从 Redis 加载 Agent 状态
     */
    public HrAgentState loadState(String sessionId) {
        try {
            String key = buildKey(sessionId);
            String json = redisTemplate.opsForValue().get(key);
            
            if (json == null) {
                return null;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            return new HrAgentState(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Agent 状态反序列化失败", e);
        }
    }
    
    /**
     * 从 Redis 删除 Agent 状态
     */
    public void deleteState(String sessionId) {
        String key = buildKey(sessionId);
        redisTemplate.delete(key);
    }
    
    /**
     * 判断指定会话是否存在状态
     */
    public boolean hasState(String sessionId) {
        String key = buildKey(sessionId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    /**
     * 清除所有 Agent 状态（用于测试/清理）
     */
    public void clearAllStates() {
        java.util.Set<String> keys = redisTemplate.keys(AGENT_STATE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
    
    // ==================== MySQL 会话持久化 ====================
    
    /**
     * 创建新会话记录
     */
    @Transactional
    public void createSession(String sessionId, Long userId, String title) {
        AgentSession session = new AgentSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setStatus(0); // 进行中
        session.setStartTime(LocalDateTime.now());
        session.setMessageCount(0);
        session.setTitle(title);
        sessionMapper.insert(session);
    }
    
    /**
     * 结束会话（软删除，标记为已结束）
     */
    @Transactional
    public void endSession(String sessionId, String intent) {
        AgentSession session = sessionMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getSessionId, sessionId)
        );
        if (session != null) {
            session.setStatus(1); // 已结束
            session.setEndTime(LocalDateTime.now());
            session.setIntent(intent);
            sessionMapper.updateById(session);
        }
    }

    /**
     * 硬删除会话（从 MySQL 中彻底删除会话及其消息）
     */
    @Transactional
    public void hardDeleteSession(String sessionId) {
        messageMapper.delete(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, sessionId)
        );
        sessionMapper.delete(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getSessionId, sessionId)
        );
    }
    
    /**
     * 保存单条消息到对话历史
     */
    @Transactional
    public void saveMessage(String sessionId, String role, String content, String messageType) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setMessageType(messageType);
        messageMapper.insert(message);
        
        // 更新会话消息计数
        sessionMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AgentSession>()
                .eq(AgentSession::getSessionId, sessionId)
                .setSql("message_count = message_count + 1")
        );
    }
    
    /**
     * 批量保存消息（事务内一次性写入）
     */
    @Transactional
    public void saveMessages(String sessionId, List<String> messages) {
        for (String msg : messages) {
            String role;
            String content;
            if (msg.startsWith(HrAgentState.USER_PREFIX)) {
                role = "user";
                content = msg.substring(HrAgentState.USER_PREFIX.length());
            } else if (msg.startsWith(HrAgentState.ASSISTANT_PREFIX)) {
                role = "assistant";
                content = msg.substring(HrAgentState.ASSISTANT_PREFIX.length());
            } else {
                // 无前缀的消息，按 assistant 角色处理
                role = "assistant";
                content = msg;
            }
            String messageType = inferMessageType(content);
            saveMessage(sessionId, role, content, messageType);
        }
    }
    
    /**
     * 根据消息内容推断消息类型
     */
    private String inferMessageType(String content) {
        if (content == null) return "text";
        if (content.startsWith("意图已识别") || content.startsWith("意图识别失败")) return "intent";
        if (content.startsWith("工具执行失败") || content.startsWith("工具执行出错")) return "error";
        if (content.startsWith("结果校验") || content.startsWith("校验失败")) return "validation";
        if (content.startsWith("正在重试")) return "tool_result";
        return "text";
    }
    
    // ==================== MySQL 工具调用日志持久化 ====================
    
    /**
     * 记录工具调用日志
     */
    @Transactional
    public void saveToolLog(String sessionId, Long messageId, String intentCode,
                           String toolName, String inputParams, String outputResult,
                           String status, Long durationMs, String errorMessage) {
        AgentToolLog log = new AgentToolLog();
        log.setSessionId(sessionId);
        log.setMessageId(messageId);
        log.setIntentCode(intentCode);
        log.setToolName(toolName);
        log.setInputParams(inputParams);
        log.setOutputResult(outputResult);
        log.setStatus(status);
        log.setDurationMs(durationMs);
        log.setErrorMessage(errorMessage);
        toolLogMapper.insert(log);
    }
    
    // ==================== 会话历史查询 ====================
    
    /**
     * 查询会话的历史消息列表
     */
    public List<AgentMessage> getSessionMessages(String sessionId) {
        return messageMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentMessage>()
                .eq(AgentMessage::getSessionId, sessionId)
                .orderByAsc(AgentMessage::getCreateTime)
        );
    }
    
    /**
     * 查询会话的工具调用日志列表
     */
    public List<AgentToolLog> getSessionToolLogs(String sessionId) {
        return toolLogMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentToolLog>()
                .eq(AgentToolLog::getSessionId, sessionId)
                .orderByAsc(AgentToolLog::getCreateTime)
        );
    }
    
    /**
     * 查询用户的活跃会话列表（排除已删除的）
     */
    public List<AgentSession> getUserSessions(Long userId) {
        return sessionMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getUserId, userId)
                .ne(AgentSession::getStatus, 2) // 排除已删除(status=2)的会话
                .orderByDesc(AgentSession::getCreateTime)
        );
    }
    
    private String buildKey(String sessionId) {
        return AGENT_STATE_PREFIX + sessionId;
    }
}