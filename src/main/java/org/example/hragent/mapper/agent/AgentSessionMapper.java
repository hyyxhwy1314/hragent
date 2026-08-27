package org.example.hragent.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.hragent.entity.agent.AgentSession;

/**
 * Agent 会话 Mapper
 */
@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSession> {
}