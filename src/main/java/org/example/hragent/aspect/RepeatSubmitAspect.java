package org.example.hragent.aspect;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.hragent.annotation.RepeatSubmit;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.utils.RedisUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交切面
 * 拦截 @RepeatSubmit 注解，使用 Redis SETNX 实现
 *
 * 原理：
 * 1. 请求进入时，按 keyType 生成唯一 key
 * 2. SETNX 写入 Redis，TTL = interval
 * 3. 写入成功 → 放行；写入失败（key 已存在）→ 重复提交，拒绝
 */
@Aspect
@Component
public class RepeatSubmitAspect {

    private final RedisUtils redisUtils;
    private static final String KEY_PREFIX = "repeat_submit:";

    public RepeatSubmitAspect(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    @Around("@annotation(repeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) throws Throwable {
        // 1. 生成防重 key
        String key = buildKey(joinPoint, repeatSubmit);

        // 2. SETNX 写入，TTL = interval
        boolean acquired = redisUtils.setIfAbsent(
                key,
                "1",
                repeatSubmit.interval(),
                repeatSubmit.unit()
        );

        if (!acquired) {
            // 3. key 已存在 → 重复提交
            throw new BusinessException(ErrorCode.REPEAT_SUBMIT, repeatSubmit.message());
        }

        // 4. 放行
        return joinPoint.proceed();
    }

    private String buildKey(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) {
        // 自定义 key 优先
        if (!repeatSubmit.key().isBlank()) {
            return KEY_PREFIX + repeatSubmit.key();
        }

        HttpServletRequest request = currentRequest();
        if (request == null) {
            // 非 Web 请求降级为方法签名
            return KEY_PREFIX + joinPoint.getSignature().toShortString();
        }

        String uri = request.getRequestURI();
        switch (repeatSubmit.keyType()) {
            case TOKEN:
                String token = request.getHeader("Authorization");
                if (token == null || token.isBlank()) token = request.getHeader("token");
                if (token == null || token.isBlank()) token = "anonymous";
                return KEY_PREFIX + "token:" + token + ":" + uri;
            case IP:
                return KEY_PREFIX + "ip:" + clientIp(request) + ":" + uri;
            case PARAMS:
            default:
                // 默认 PARAMS：Token + URI + 请求体哈希
                String t = request.getHeader("Authorization");
                if (t == null || t.isBlank()) t = request.getHeader("token");
                if (t == null || t.isBlank()) t = clientIp(request);
                String argsHash = md5(joinPoint.getArgs());
                return KEY_PREFIX + "params:" + t + ":" + uri + ":" + argsHash;
        }
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
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String md5(Object[] args) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                if (arg != null) sb.append(arg.toString());
            }
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(sb.toString().getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                String h = Integer.toHexString(b & 0xff);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            return String.valueOf(args.hashCode());
        }
    }
}
