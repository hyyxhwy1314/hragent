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
 * 注意：切点使用全限定名 @annotation，不依赖参数绑定
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
        log.info("✅ DistributedLockAspect 已实例化，RedissonClient={}", redissonClient.getClass().getSimpleName());
    }

    @Around("@annotation(org.example.hragent.annotation.DistributedLock)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);
        if (distributedLock == null) {
            return joinPoint.proceed();
        }

        String lockKey = buildLockKey(joinPoint, distributedLock, method);
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.unit()
            );
            if (!locked) {
                log.warn("🔒 分布式锁获取失败 key={} waitTime={}s method={}",
                        lockKey, distributedLock.waitTime(), method.getName());
                throw new DistributedLockTimeoutException(distributedLock.message());
            }
            log.debug("🔒 分布式锁获取成功 key={}", lockKey);
            return joinPoint.proceed();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("🔓 分布式锁已释放 key={}", lockKey);
            }
        }
    }

    private String buildLockKey(ProceedingJoinPoint joinPoint, DistributedLock distributedLock, Method method) {
        String keyExpr = distributedLock.key();
        String prefix = distributedLock.prefix();
        if (!keyExpr.contains("#")) {
            return prefix + ":" + keyExpr;
        }
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
