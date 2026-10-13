package org.example.hragent.config;

import org.example.hragent.interceptor.AuthInterceptor;
import org.example.hragent.interceptor.BehaviorLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * <p>
 * 注册鉴权拦截器与行为日志拦截器，放行登录接口与静态资源；配置跨域（前后端分离）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final BehaviorLogInterceptor behaviorLogInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor, BehaviorLogInterceptor behaviorLogInterceptor) {
        this.authInterceptor = authInterceptor;
        this.behaviorLogInterceptor = behaviorLogInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",         // 登录接口
                        "/error",               // Spring 错误页
                        "/favicon.ico",
                        // 静态资源放行
                        "/index.html",
                        "/assets/**",
                        "/*.js",
                        "/*.css",
                        "/*.html",
                        "/*.ico"
                );

        // 行为日志采集（必须在鉴权之后执行，以便能取到当前登录用户）
        registry.addInterceptor(behaviorLogInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/error",
                        "/favicon.ico"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
