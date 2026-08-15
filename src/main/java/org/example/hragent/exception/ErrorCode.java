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

    //登录鉴权 2xxx
    TOKEN_INVALID(2001, "token无效或已过期"),
    LOGIN_FAIL(2002, "账号密码错误"),

    //业务模块 3xxx
    EMP_NOT_EXIST(3001, "员工不存在"),
    JOB_POST_NOT_EXIST(3002, "岗位不存在"),
    RESUME_NOT_EXIST(3003, "简历不存在");

    private final Integer code;
    private final String msg;

    ErrorCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}