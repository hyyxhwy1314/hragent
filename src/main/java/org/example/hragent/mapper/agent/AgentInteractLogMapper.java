package org.example.hragent.mapper.agent;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.hragent.entity.agent.AgentInteractLog;

import java.util.List;
import java.util.Map;

/**
 * AI 交互日志 Mapper
 */
@Mapper
public interface AgentInteractLogMapper extends BaseMapper<AgentInteractLog> {

    /**
     * 按天统计交互量、Token 消耗与平均耗时
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS dateKey, " +
            "       COUNT(*)                    AS totalCount, " +
            "       SUM(tool_used)              AS toolUsedCount, " +
            "       COALESCE(SUM(input_tokens),0)  AS totalInputTokens, " +
            "       COALESCE(SUM(output_tokens),0) AS totalOutputTokens, " +
            "       COALESCE(ROUND(AVG(duration_ms)),0) AS avgDurationMs " +
            "FROM t_agent_interact_log " +
            "WHERE is_deleted = 0 " +
            "<if test='days != null'> AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if> " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "ORDER BY dateKey ASC" +
            "</script>")
    List<Map<String, Object>> statDaily(@Param("days") Integer days);

    /**
     * 工具调用分布统计（近 N 天 top 工具）
     */
    @Select("<script>" +
            "SELECT tool_name AS name, COUNT(*) AS value " +
            "FROM t_agent_tool_log " +
            "WHERE is_deleted = 0 " +
            "<if test='days != null'> AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if> " +
            "GROUP BY tool_name " +
            "ORDER BY value DESC LIMIT 10" +
            "</script>")
    List<Map<String, Object>> statToolDistribution(@Param("days") Integer days);

    /**
     * 统计汇总指标（近 N 天）
     */
    @Select("<script>" +
            "SELECT COUNT(*)                    AS totalCount, " +
            "       COALESCE(SUM(tool_call_count),0) AS totalToolCalls, " +
            "       COALESCE(SUM(input_tokens + output_tokens),0) AS totalTokens, " +
            "       COALESCE(ROUND(AVG(duration_ms)),0) AS avgDurationMs, " +
            "       COALESCE(SUM(has_error),0)  AS errorCount " +
            "FROM t_agent_interact_log " +
            "WHERE is_deleted = 0 " +
            "<if test='days != null'> AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if>" +
            "</script>")
    Map<String, Object> statSummary(@Param("days") Integer days);
}