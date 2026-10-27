package org.example.hragent.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.hragent.annotation.RequireRole;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.utils.CurrentUserService;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 角色授权拦截器
 * <p>
 * 校验 {@link RequireRole} 声明的角色：方法级优先于类级（类级会沿父类向上查找）。
 * 需求：在 {@link AuthInterceptor} 之后执行，以便从 {@link CurrentUserService} 取到当前登录人。
 * 未登录或无权限时抛 {@link BusinessException}，由全局异常处理器统一返回 JSON。
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRole requireRole = resolve(handlerMethod);
        if (requireRole == null) {
            return true;
        }
        CurrentUserService.CurrentUser user = CurrentUserService.get();
        String role = user == null ? null : user.getRole();
        boolean allowed = role != null && Arrays.asList(requireRole.value()).contains(role);
        BusinessException.throwIf(!allowed, ErrorCode.NO_PERMISSION);
        return true;
    }

    /** 解析角色限制：先看方法级，再看类级（含父类） */
    private RequireRole resolve(HandlerMethod handlerMethod) {
        RequireRole onMethod = AnnotatedElementUtils.getMergedAnnotation(handlerMethod.getMethod(), RequireRole.class);
        if (onMethod != null) {
            return onMethod;
        }
        for (Class<?> type = handlerMethod.getBeanType(); type != null && type != Object.class; type = type.getSuperclass()) {
            RequireRole onType = AnnotatedElementUtils.getMergedAnnotation(type, RequireRole.class);
            if (onType != null) {
                return onType;
            }
        }
        return null;
    }
}
