package org.example.hragent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交注解
 * 基于 Redis SETNX 实现，用于防止用户快速重复点击导致重复提交
 *
 * 使用场景：表单提交、订单创建、简历投递等幂等性要求高的接口
 *
 * 使用示例：
 * <pre>
 * &#64;RepeatSubmit(interval = 5, message = "请勿重复提交")
 * &#64;PostMapping
 * public R&lt;Void&gt; submit(@RequestBody ResumeDto dto) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatSubmit {

    /**
     * 防重间隔（默认 5 秒内视为重复）
     */
    long interval() default 5;

    /**
     * 时间单位
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 防重 key 生成策略：
     * - TOKEN：基于 请求头 Token + URI
     * - PARAMS：基于 Token + URI + 请求体 MD5（默认，更严格）
     * - IP：基于 客户端 IP + URI
     */
    KeyType keyType() default KeyType.PARAMS;

    /**
     * 自定义 key 后缀（SpEL），与 keyType 二选一
     * 留空则按 keyType 自动生成
     */
    String key() default "";

    /**
     * 重复提交提示信息
     */
    String message() default "请勿重复提交，请稍后再试";

    /**
     * Key 生成策略
     */
    enum KeyType {
        /** 请求头 Token + URI */
        TOKEN,
        /** Token + URI + 请求体 MD5 */
        PARAMS,
        /** 客户端 IP + URI */
        IP
    }
}