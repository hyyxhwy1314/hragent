package org.example.hragent.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Flowable 7.x 引擎配置
 * <p>
 * 关键点：
 * <ul>
 *   <li>复用 Spring 事务管理器（Flowable 自动加入 Spring 事务，业务表与流程表同库同事务）</li>
 *   <li>开启异步执行器：自动节点（ServiceTask）异步触发，否则流程卡在自动节点不动</li>
 *   <li>历史归档保留时长在 application.yaml 中配置 flowable.history-time-to-live</li>
 *   <li>关闭引擎自带鉴权，统一走应用层 AOP（@DistributedLock / @RateLimit / @RepeatSubmit）</li>
 * </ul>
 */
@Configuration
public class FlowableConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableConfigurer() {
        return configuration -> {
            // 异步执行器：自动节点（ServiceTask）异步触发，原 application.yaml 中为 false
            configuration.setAsyncExecutorActivate(true);
        };
    }

    /**
     * 密码哈希编码器（BCrypt），用于登录校验与新增员工时设置默认密码
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
