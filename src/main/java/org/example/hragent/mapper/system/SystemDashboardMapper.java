package org.example.hragent.mapper.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 系统数据看板 Mapper
 * <p>
 * 聚合整个系统各类业务数据的统计，展示完整系统运行状况，
 * 不仅包含 AI 交互数据，也包含 HR 业务操作数据。
 */
@Mapper
public interface SystemDashboardMapper {

    /**
     * 系统总体汇总统计（近 N 天）
     * 聚合 AI 交互 + HR 业务各模块数据
     */
    @Select("<script>" +
            "SELECT " +
            "  (SELECT COUNT(*) FROM t_agent_interact_log WHERE is_deleted = 0 " +
            "  <if test='days != null'> AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if>" +
            "  ) AS aiTotalCount, " +
            "  (SELECT COUNT(*) FROM t_employee WHERE is_deleted = 0) AS employeeCount, " +
            "  (SELECT COUNT(*) FROM t_resume WHERE is_deleted = 0) AS resumeCount, " +
            "  (SELECT COUNT(*) FROM t_flow_instance WHERE is_deleted = 0) AS flowCount, " +
            "  (SELECT COUNT(*) FROM t_job_post WHERE is_deleted = 0) AS jobPostCount, " +
            "  (SELECT COUNT(*) FROM t_performance WHERE is_deleted = 0) AS performanceCount, " +
            "  (SELECT COUNT(*) FROM t_training_course WHERE is_deleted = 0) AS trainingCount " +
            "</script>")
    Map<String, Object> statSystemSummary(@Param("days") Integer days);

    /**
     * 按天统计业务操作（近 N 天）
     * 展示各业务模块日增量变化
     */
    @Select("<script>" +
            "SELECT " +
            "  dates.dt AS dateKey, " +
            "  COUNT(fl.id) AS flowCount, " +
            "  COUNT(fr.id) AS resumeCount, " +
            "  COUNT(pr.id) AS performanceCount " +
            "FROM ( " +
            "  SELECT DISTINCT DATE_FORMAT(create_time, '%Y-%m-%d') AS dt FROM t_flow_instance WHERE is_deleted = 0 " +
            "  <if test='days != null'> AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if> " +
            ") dates " +
            "LEFT JOIN t_flow_instance fl ON DATE_FORMAT(fl.create_time, '%Y-%m-%d') = dates.dt AND fl.is_deleted = 0 " +
            "LEFT JOIN t_resume fr ON DATE_FORMAT(fr.create_time, '%Y-%m-%d') = dates.dt AND fr.is_deleted = 0 " +
            "LEFT JOIN t_performance pr ON DATE_FORMAT(pr.create_time, '%Y-%m-%d') = dates.dt AND pr.is_deleted = 0 " +
            "GROUP BY dates.dt " +
            "ORDER BY dates.dt ASC " +
            "</script>")
    List<Map<String, Object>> statDailyBusiness(@Param("days") Integer days);

    /**
     * 流程审批分布统计（近 N 天）
     * 统计各类型流程发起量
     */
    @Select("<script>" +
            "SELECT flow_type AS name, COUNT(*) AS value " +
            "FROM t_flow_instance " +
            "WHERE is_deleted = 0 " +
            "<if test='days != null'> AND create_time &gt;= DATE_SUB(NOW(), INTERVAL #{days} DAY)</if> " +
            "GROUP BY flow_type " +
            "ORDER BY value DESC " +
            "</script>")
    List<Map<String, Object>> statFlowDistribution(@Param("days") Integer days);

    /**
     * HR 招聘漏斗统计
     * 统计简历各状态数量，展示招聘转化漏斗
     */
    @Select("SELECT resume_status AS `status_key`, COUNT(*) AS value " +
            "FROM t_resume " +
            "WHERE is_deleted = 0 " +
            "GROUP BY resume_status " +
            "ORDER BY resume_status ASC")
    List<Map<String, Object>> statRecruitmentFunnel();

    /**
     * 最近 AI 交互日志
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS createTime, " +
            "SUBSTRING(user_message, 1, 60) AS userMessage, " +
            "SUBSTRING(answer, 1, 100) AS aiAnswer, " +
            "input_tokens AS inputTokens, " +
            "output_tokens AS outputTokens " +
            "FROM t_agent_interact_log " +
            "WHERE is_deleted = 0 " +
            "ORDER BY create_time DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> statRecentAiLogs(@Param("limit") Integer limit);

    /**
     * 最近流程审批日志（含申请人信息）
     */
    @Select("SELECT DATE_FORMAT(fl.create_time, '%Y-%m-%d %H:%i') AS createTime, " +
            "fl.flow_type AS flowType, " +
            "fl.flow_no AS flowNo, " +
            "fl.flow_status AS flowStatus, " +
            "fl.apply_emp_id AS applyEmpId, " +
            "emp.emp_name AS applicantName " +
            "FROM t_flow_instance fl " +
            "LEFT JOIN t_employee emp ON fl.apply_emp_id = emp.id " +
            "WHERE fl.is_deleted = 0 " +
            "ORDER BY fl.create_time DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> statRecentFlowLogs(@Param("limit") Integer limit);

}