package org.example.hragent.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.hragent.entity.agent.AgentToolLog;

/**
 * 工具调用日志 Mapper
 */
@Mapper
public interface AgentToolLogMapper extends BaseMapper<AgentToolLog> {
}