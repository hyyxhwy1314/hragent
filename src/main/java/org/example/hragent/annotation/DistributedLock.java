package org.example.hragent.annotation;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解
 * 基于 Redisson RLock 实现，用于保护关键业务（如：扣库存、生成单号、防并发修改）
 *
 * 使用示例：
 * <pre>
 * &#64;DistributedLock(key = "order:create:#{#userId}", waitTime = 3, leaseTime = 10)
 * public void createOrder(Long userId) { ... }
 * </pre>
 *
 * key 支持 SpEL：#{#参数名} #{#user.id} #{T(String).valueOf(#req.userId)}
 */
public @interface DistributedLock {

    /**
     * 锁的 key（支持 SpEL）
     * 最终存储为 redis key: "lock:" + key
     */
    String key();

    /**
     * 前缀，默认 lock:
     */
    String prefix() default "lock";

    /**
     * 获取锁的最大等待时间（秒）
     * 超过此时间没拿到锁，抛 DistributedLockTimeoutException
     */
    long waitTime() default 3;

    /**
     * 持有锁的最大时长（秒），超时自动释放（防止死锁）
     * -1 表示 Redisson 默认行为（看门狗续期 30 秒）
     */
    long leaseTime() default -1;

    /**
     * 时间单位
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 拿不到锁时的提示信息
     */
    String message() default "系统繁忙，请稍后重试";
}
