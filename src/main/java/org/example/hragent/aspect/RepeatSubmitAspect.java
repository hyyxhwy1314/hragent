package org.example.hragent.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.hragent.annotation.RepeatSubmit;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.utils.RedisUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.security.MessageDigest;

/**
 * 防重复提交切面
 * 拦截 @RepeatSubmit 注解，使用 Redis SETNX 实现
 * 注意：切点使用全限定名 @annotation，不依赖参数绑定
 */
@Slf4j
@Aspect
@Component
public class RepeatSubmitAspect {

    private final RedisUtils redisUtils;
    private static final String KEY_PREFIX = "repeat_submit:";

    public RepeatSubmitAspect(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
        log.info("✅ RepeatSubmitAspect 已实例化，RedisUtils={}", redisUtils.getClass().getSimpleName());
    }

    @Around("@annotation(org.example.hragent.annotation.RepeatSubmit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RepeatSubmit repeatSubmit = method.getAnnotation(RepeatSubmit.class);
        if (repeatSubmit == null) {
            return joinPoint.proceed();
        }

        String key = buildKey(joinPoint, repeatSubmit);
        boolean acquired = redisUtils.setIfAbsent(key, "1", repeatSubmit.interval(), repeatSubmit.unit());
        if (!acquired) {
            log.warn("⏱ 防重复提交触发 key={} method={}", key, method.getName());
            throw new BusinessException(ErrorCode.REPEAT_SUBMIT, repeatSubmit.message());
        }
        log.debug("⏱ 防重复提交首次通过 key={}", key);
        return joinPoint.proceed();
    }

    private String buildKey(ProceedingJoinPoint joinPoint, RepeatSubmit repeatSubmit) {
        if (!repeatSubmit.key().isBlank()) {
            return KEY_PREFIX + repeatSubmit.key();
        }
        HttpServletRequest request = currentRequest();
        if (request == null) {
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
