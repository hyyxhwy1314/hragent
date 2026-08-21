package org.example.hragent.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.utils.CurrentUserService;
import org.example.hragent.utils.JwtUtils;
import org.example.hragent.vo.R;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器
 * <p>
 * 校验请求头 Authorization 中的 JWT token，通过后注入当前用户到 {@link CurrentUserService}。
 * token 缺失/过期/非法返回 401，前端响应拦截器跳转登录页。
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtUtils jwtUtils, ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 预检放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return unauthorized(response, "未登录");
        }

        String token = authHeader.substring(7);
        Claims claims = jwtUtils.parse(token);
        if (claims == null) {
            return unauthorized(response, "token 已过期或无效");
        }

        // 注入当前用户到 ThreadLocal
        Long empId = claims.get("empId", Long.class);
        String empName = claims.get("empName", String.class);
        String role = claims.get("role", String.class);
        CurrentUserService.set(new CurrentUserService.CurrentUser(empId, empName, role));

        log.debug("鉴权通过 empId={}, empName={}, role={}", empId, empName, role);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清除 ThreadLocal，避免内存泄漏
        CurrentUserService.clear();
    }

    private boolean unauthorized(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(401, msg)));
        return false;
    }
}
