package org.example.hragent.entity.system;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.example.hragent.entity.BaseEntity;

/**
 * 用户行为日志表
 * <p>
 * 由全局行为日志拦截器统一采集，覆盖几乎所有用户操作行为：
 * 简历分析、AI 交互、流程审批、员工管理、岗位/绩效/培训/合同等。
 * 用于工作台 Token 消耗看板与操作日志时间线展示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_behavior_log")
public class BehaviorLog extends BaseEntity {

    /** 操作人 ID（t_employee.id） */
    @TableField("user_id")
    private Long userId;

    /** 操作人姓名 */
    @TableField("username")
    private String username;

    /** 业务模块（简历分析/AI交互/流程/员工等） */
    @TableField("module")
    private String module;

    /** 动作名称（如 深度分析/智能问答/发起审批） */
    @TableField("action")
    private String action;

    /** 操作摘要 */
    @TableField("summary")
    private String summary;

    /** 详情（JSON 字符串） */
    @TableField("detail")
    private String detail;

    /** HTTP 方法 */
    @TableField("http_method")
    private String httpMethod;

    /** 请求路径 */
    @TableField("request_uri")
    private String requestUri;

    /** 状态：success 成功 / fail 失败 */
    @TableField("status")
    private String status;

    /** 请求耗时（毫秒） */
    @TableField("duration_ms")
    private Long durationMs;

    /** 本次操作 Token 消耗（仅供 AI 类） */
    @TableField("token_count")
    private Integer tokenCount;
}