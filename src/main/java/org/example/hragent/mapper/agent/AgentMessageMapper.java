package org.example.hragent.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.hragent.entity.agent.AgentMessage;

/**
 * 对话历史 Mapper
 */
@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {
}