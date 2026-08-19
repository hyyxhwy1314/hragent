package org.example.hragent.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.hragent.annotation.RateLimit;
import org.example.hragent.exception.RateLimitException;
import org.example.hragent.utils.RedisUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流切面
 * 实现方式：Redis 固定窗口计数器（INCR + TTL）
 * 注意：切点使用全限定名 @annotation，不依赖参数绑定，避免 Spring AOP 解析歧义
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private final RedisUtils redisUtils;
    private static final String KEY_PREFIX = "rate_limit:";

    public RateLimitAspect(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
        log.info("✅ RateLimitAspect 已实例化，RedisUtils={}", redisUtils.getClass().getSimpleName());
    }

    @Around("@annotation(org.example.hragent.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return joinPoint.proceed();
        }

        String key = buildKey(joinPoint, rateLimit);
        long count = redisUtils.increment(key);
        if (count == 1) {
            redisUtils.expire(key, rateLimit.rateInterval(), rateLimit.rateIntervalUnit());
        }

        if (count > rateLimit.rate()) {
            log.warn("🚦 接口限流触发 key={} count={}/{} method={}",
                    key, count, rateLimit.rate(), method.getName());
            throw new RateLimitException(rateLimit.message());
        }

        log.debug("🚦 限流放行 key={} count={}/{}", key, count, rateLimit.rate());
        return joinPoint.proceed();
    }

    private String buildKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        if (!rateLimit.key().isBlank()) {
            return KEY_PREFIX + rateLimit.key();
        }
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return KEY_PREFIX + joinPoint.getSignature().toShortString();
        }
        String uri = request.getRequestURI();
        String ip = clientIp(request);
        return KEY_PREFIX + uri + ":" + ip;
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
