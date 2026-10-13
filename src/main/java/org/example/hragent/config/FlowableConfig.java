package org.example.hragent.config;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.flow.FlowProcessEventListener;
import org.flowable.engine.RuntimeService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.boot.ApplicationRunner;
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
 *   <li>注册流程结束事件监听器：兜底同步业务表状态，避免"已完成流程仍显示进行中"</li>
 *   <li>历史归档保留时长在 application.yaml 中配置 flowable.history-time-to-live</li>
 * </ul>
 */
@Slf4j
@Configuration
public class FlowableConfig {

    /**
     * 引擎配置：开启异步执行器
     */
    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableConfigurer() {
        return configuration -> {
            // 异步执行器：自动节点（ServiceTask）异步触发
            configuration.setAsyncExecutorActivate(true);
        };
    }

    /**
     * 注册流程结束事件监听器（应用启动完成后执行，引擎已就绪）
     */
    @Bean
    public ApplicationRunner flowEventListenerRegistrar(RuntimeService runtimeService,
                                                        FlowProcessEventListener flowProcessEventListener) {
        return args -> {
            runtimeService.addEventListener(flowProcessEventListener);
            log.info("流程结束事件监听器已注册到 Flowable 引擎");
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
