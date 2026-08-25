package org.example.hragent.exception;
import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "系统内部异常"),

    //业务 1xxx
    PARAM_ERROR(1001, "参数校验失败"),
    DATA_NOT_FOUND(1002, "数据不存在"),
    DATA_EXIST(1003, "数据已存在"),
    NO_PERMISSION(1004, "权限不足"),
    OPERATION_FAILED(1005, "操作失败"),
    REPEAT_SUBMIT(1006, "请勿重复提交"),
    RATE_LIMITED(1007, "请求过于频繁"),
    LOCK_TIMEOUT(1008, "分布式锁获取超时"),

    //登录鉴权 2xxx
    TOKEN_INVALID(2001, "token无效或已过期"),
    LOGIN_FAIL(2002, "账号或密码错误"),
    ACCOUNT_DISABLED(2003, "账号已禁用"),
    UNAUTHORIZED(20401, "未登录"),
    FORBIDDEN(20403, "权限不足"),

    //业务模块 3xxx
    EMP_NOT_EXIST(3001, "员工不存在"),
    JOB_POST_NOT_EXIST(3002, "岗位不存在"),
    RESUME_NOT_EXIST(3003, "简历不存在"),
    LEADER_NOT_EXIST(3004, "领导不存在"),

    //工作流 4xxx
    FLOW_DEFINITION_NOT_FOUND(4001, "流程定义不存在"),
    FLOW_INSTANCE_NOT_FOUND(4002, "流程实例不存在"),
    FLOW_ALREADY_FINISHED(4003, "流程已结束"),
    FLOW_TASK_NOT_FOUND(4004, "任务不存在"),
    FLOW_TASK_NOT_ASSIGNEE(4005, "任务非本人处理"),
    FLOW_APPROVER_NOT_FOUND(4006, "审批人未找到"),
    FLOW_START_FAILED(4007, "流程发起失败"),
    FLOW_DEPLOY_FAILED(4009, "流程部署失败");


    private final Integer code;
    private final String msg;

    ErrorCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}