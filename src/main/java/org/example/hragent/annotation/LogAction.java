package org.example.hragent.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 行为日志注解
 * <p>
 * 标注在 Controller 方法上，用于覆盖/增强全局行为日志的语义信息：
 * <ul>
 *     <li>{@link #module()} 指定归属业务模块（如 简历分析/AI交互）</li>
 *     <li>{@link #action()} 指定动作名称（如 深度分析/发起审批）</li>
 *     <li>{@link #markToken()} 标记当前操作是否产生 Token 消耗（AI 类）</li>
 * </ul>
 * 未标注时，由全局拦截器根据请求路径与 HTTP 方法推断模块与动作。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogAction {

    /** 业务模块（如 简历分析/AI交互/流程审批） */
    String module() default "";

    /** 动作名称（如 深度分析/智能问答/发起审批） */
    String action() default "";

    /** 是否产生 Token 消耗（AI 类操作为 true） */
    boolean markToken() default false;
}