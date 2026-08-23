package org.example.hragent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求
 */
@Data
public class LoginDto {

    /** 工号或手机号 */
    @NotBlank(message = "账号不能为空")
    private String account;

    /** 密码明文 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
