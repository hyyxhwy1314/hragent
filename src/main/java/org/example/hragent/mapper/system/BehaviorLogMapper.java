package org.example.hragent.mapper.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.hragent.entity.system.BehaviorLog;

import java.util.List;
import java.util.Map;

/**
 * 用户行为日志 Mapper
 * <p>
 * 提供行为日志的查询聚合，支撑工作台 Token 消耗看板与操作日志时间线。
 */
@Mapper
public interface BehaviorLogMapper extends BaseMapper<BehaviorLog> {

    /**
     * Token 消耗汇总（输入/输出拆分, 今日/周期/峰值）
     * <p>
     * 从 AI 交互日志聚合 Token 统计：
     * 今日消耗、周期内消耗、周期日均、峰值日及峰值，并拆分输入/输出。
     */
    @Select("<script>" +
            "SELECT " +
            "  COALESCE(SUM(CASE WHEN DATE(create_time) = CURDATE() THEN input_tokens + output_tokens END), 0) AS todayTotal, " +
            "  COALESCE(SUM(CASE WHEN DATE(create_time) = CURDATE() THEN input_tokens END), 0) AS todayInput, " +
            "  COALESCE(SUM(CASE WHEN DATE(create_time) = CURDATE() THEN output_tokens END), 0) AS todayOutput, " +
            "  COALESCE(SUM(input_tokens + output_tokens), 0) AS periodTotal, " +
            "  COALESCE(SUM(input_tokens), 0) AS periodInput, " +
            "  COALESCE(SUM(output_tokens), 0) AS periodOutput, " +
            "  COALESCE(ROUND(SUM(input_tokens + output_tokens) / #{days}), 0) AS avgPerDay " +
            "FROM t_agent_interact_log " +
            "WHERE is_deleted = 0 " +
            "  AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)" +
            "</script>")
    Map<String, Object> statTokenSummary(@Param("days") Integer days);

    /**
     * Token 消耗峰值日（近 N 天）
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS peakDay, " +
            "       COALESCE(SUM(input_tokens + output_tokens), 0) AS peakValue " +
            "FROM t_agent_interact_log " +
            "WHERE is_deleted = 0 " +
            "<if test='days != null'> AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if> " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "ORDER BY peakValue DESC LIMIT 1" +
            "</script>")
    Map<String, Object> statTokenPeak(@Param("days") Integer days);

    /**
     * Token 按天趋势（输入/输出拆分）
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS dateKey, " +
            "       COALESCE(SUM(input_tokens), 0) AS inputTokens, " +
            "       COALESCE(SUM(output_tokens), 0) AS outputTokens " +
            "FROM t_agent_interact_log " +
            "WHERE is_deleted = 0 " +
            "<if test='days != null'> AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if> " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "ORDER BY dateKey ASC" +
            "</script>")
    List<Map<String, Object>> statTokenDaily(@Param("days") Integer days);

    /**
     * 行为日志按天统计（供行为趋势/总量展示）
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS dateKey, COUNT(*) AS value " +
            "FROM t_behavior_log " +
            "WHERE is_deleted = 0 " +
            "<if test='days != null'> AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if> " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "ORDER BY dateKey ASC" +
            "</script>")
    List<Map<String, Object>> statBehaviorDaily(@Param("days") Integer days);

    /**
     * 行为日志按模块统计（数量与 Token）
     */
    @Select("<script>" +
            "SELECT module AS name, " +
            "       COUNT(*) AS value, " +
            "       COALESCE(SUM(token_count), 0) AS tokenCount " +
            "FROM t_behavior_log " +
            "WHERE is_deleted = 0 " +
            "<if test='days != null'> AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if> " +
            "GROUP BY module " +
            "ORDER BY value DESC" +
            "</script>")
    List<Map<String, Object>> statBehaviorModule(@Param("days") Integer days);

    /**
     * 分页查询行为日志（按条件过滤：模块/关键词/状态），按时间倒序
     */
    @Select("<script>" +
            "SELECT id, user_id AS userId, username, module, action, summary, detail, " +
            "       http_method AS httpMethod, request_uri AS requestUri, status, " +
            "       duration_ms AS durationMs, token_count AS tokenCount, " +
            "       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime " +
            "FROM t_behavior_log " +
            "WHERE is_deleted = 0 " +
            "<if test='module != null and module != \"\"'> AND module = #{module}</if> " +
            "<if test='keyword != null and keyword != \"\"'> AND (summary LIKE CONCAT('%', #{keyword}, '%') OR action LIKE CONCAT('%', #{keyword}, '%'))</if> " +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if> " +
            "ORDER BY id DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<Map<String, Object>> statBehaviorLogs(@Param("module") String module,
                                               @Param("keyword") String keyword,
                                               @Param("status") String status,
                                               @Param("offset") int offset,
                                               @Param("size") int size);

    /**
     * 行为日志总数（与分页查询同条件）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM t_behavior_log " +
            "WHERE is_deleted = 0 " +
            "<if test='module != null and module != \"\"'> AND module = #{module}</if> " +
            "<if test='keyword != null and keyword != \"\"'> AND (summary LIKE CONCAT('%', #{keyword}, '%') OR action LIKE CONCAT('%', #{keyword}, '%'))</if> " +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if> " +
            "</script>")
    long countBehaviorLogs(@Param("module") String module,
                           @Param("keyword") String keyword,
                           @Param("status") String status);

    /**
     * 最近活动时间线：排除纯 GET 查询噪音，只取真实操作（写操作 / AI 交互 / 流程审批），按时间倒序
     */
    @Select("SELECT id, user_id AS userId, username, module, action, summary, detail, " +
            "       http_method AS httpMethod, request_uri AS requestUri, status, " +
            "       duration_ms AS durationMs, token_count AS tokenCount, " +
            "       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime " +
            "FROM t_behavior_log " +
            "WHERE is_deleted = 0 AND http_method <> 'GET' " +
            "ORDER BY id DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> statRecentActivity(@Param("limit") int limit);
}