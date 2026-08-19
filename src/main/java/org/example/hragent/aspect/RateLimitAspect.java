package org.example.hragent.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.hragent.annotation.RateLimit;
import org.example.hragent.exception.RateLimitException;
import org.example.hragent.utils.RedisUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 接口限流切面
 *
 * 实现方式：Redis 固定窗口计数器
 * 原理：
 * 1. 每个请求进来，对 key 做 INCR
 * 2. 第一次请求时设置 TTL = rateInterval
 * 3. 如果计数 > rate，拒绝
 *
 * 相比 Redisson RRateLimiter 的优势：
 * - 不存在配置残留问题（trySetRate 只首次生效）
 * - 逻辑简单可靠，易于调试
 * - 每个 key 独立计数窗口
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private final RedisUtils redisUtils;
    private static final String KEY_PREFIX = "rate_limit:";

    public RateLimitAspect(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
        log.info("✅ RateLimitAspect 已加载，RedisUtils={}", redisUtils.getClass().getName());
    }

    /**
     * 切点：org.example.hragent 包下任意带 @RateLimit 注解的方法
     * 使用 @within 与 @annotation 双重匹配，确保代理生效
     */
    @Around("execution(* org.example.hragent..*(..)) && @annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        log.info("🚦 RateLimitAspect 拦截到方法: {}", joinPoint.getSignature().toShortString());
        // 1. 生成限流 key
        String key = buildKey(joinPoint, rateLimit);

        // 2. INCR 计数
        long count = redisUtils.increment(key);

        // 3. 第一次请求时设置 TTL（窗口）
        if (count == 1) {
            redisUtils.expire(key, rateLimit.rateInterval(), rateLimit.rateIntervalUnit());
        }

        // 4. 超过限流阈值 → 拒绝
        if (count > rateLimit.rate()) {
            log.warn("接口限流 key={} count={}/{}", key, count, rateLimit.rate());
            throw new RateLimitException(rateLimit.message());
        }

        log.debug("限流放行 key={} count={}/{}", key, count, rateLimit.rate());
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
