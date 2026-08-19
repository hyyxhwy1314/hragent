package org.example.hragent.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.hragent.annotation.DistributedLock;
import org.example.hragent.exception.DistributedLockTimeoutException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 分布式锁切面
 * 拦截 @DistributedLock 注解，自动加锁/释放锁
 *
 * 关键点：
 * 1. 用 try-finally 保证锁一定释放
 * 2. waitTime 超时抛业务异常，避免长时间阻塞
 * 3. leaseTime = -1 时 Redisson 自动看门狗续期
 */
@Slf4j
@Aspect
@Component
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    public DistributedLockAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        // 1. 解析 SpEL 得到最终 key
        String lockKey = buildLockKey(joinPoint, distributedLock);

        // 2. 获取 Redisson 锁
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            // 3. 尝试加锁
            locked = lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.unit()
            );

            if (!locked) {
                log.warn("分布式锁获取失败 key={} waitTime={}s", lockKey, distributedLock.waitTime());
                throw new DistributedLockTimeoutException(distributedLock.message());
            }

            log.debug("分布式锁获取成功 key={}", lockKey);

            // 4. 执行业务方法
            return joinPoint.proceed();
        } finally {
            // 5. 释放锁（仅当前线程持有锁时才释放）
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("分布式锁已释放 key={}", lockKey);
            }
        }
    }

    /**
     * 解析 SpEL 表达式生成锁 key
     */
    private String buildLockKey(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) {
        String keyExpr = distributedLock.key();
        String prefix = distributedLock.prefix();

        // 如果不含 SpEL 占位符，直接用字面量
        if (!keyExpr.contains("#")) {
            return prefix + ":" + keyExpr;
        }

        // 含 SpEL，解析参数
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = nameDiscoverer.getParameterNames(method);

        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        Expression expression = parser.parseExpression(keyExpr);
        String evaluated = expression.getValue(context, String.class);
        return prefix + ":" + evaluated;
    }
}
