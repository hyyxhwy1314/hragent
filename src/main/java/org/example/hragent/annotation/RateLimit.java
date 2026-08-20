package org.example.hragent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流注解
 * 基于 Redis 计数器实现，用于公开接口防刷
 *
 * 使用场景：验证码发送、登录、简历投递、对外 API 等
 *
 * 使用示例：
 * <pre>
 * // 每秒最多 10 个请求
 * &#64;RateLimit(rate = 10, rateInterval = 1, message = "请求过于频繁")
 * &#64;PostMapping("/verify-code")
 * public R&lt;Void&gt; sendVerifyCode(String phone) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流 key（支持 SpEL），留空则用 URI + IP
     */
    String key() default "";

    /**
     * 时间窗口内允许的请求数量
     */
    long rate() default 10;

    /**
     * 时间窗口大小
     */
    long rateInterval() default 1;

    /**
     * 时间单位
     */
    TimeUnit rateIntervalUnit() default TimeUnit.SECONDS;

    /**
     * 限流后提示信息
     */
    String message() default "请求过于频繁，请稍后再试";
}