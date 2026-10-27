package org.example.hragent.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口角色授权注解
 * <p>
 * 标注在 Controller 类或方法上，限制仅指定角色可访问；方法级优先于类级。
 * 未标注则表示不限制角色（仅需登录）。
 * 当前登录用户角色不在允许集合内时，返回 {@code ErrorCode.NO_PERMISSION(1004)}。
 *
 * <pre>
 * &#64;RequireRole({"HR", "ADMIN"})
 * &#64;RestController
 * public class EmployeeController { ... }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /** 允许访问的角色集合，取值与 t_employee.role 一致（EMPLOYEE/DEPT_LEADER/HR/HRBP/ADMIN） */
    String[] value();
}
