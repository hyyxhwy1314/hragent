package org.example.hragent.vo;

import lombok.Data;

/**
 * 登录返回
 */
@Data
public class LoginVO {

    /** JWT token */
    private String token;

    /** 员工ID */
    private Long empId;

    /** 姓名 */
    private String empName;

    /** 角色 */
    private String role;
}
