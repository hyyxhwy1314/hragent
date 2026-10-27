package org.example.hragent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 飞书开放平台原生 OpenAPI 接入配置（用于审批卡片推送与回调，区别于云文档 MCP）。
 * <p>
 * 与云文档 MCP（{@link FeishuMcpProperties}）复用同一个飞书自建应用：
 * 应用身份（tenant_access_token）由 SDK/内置客户端自动获取，无需手工维护 UAT。
 * <p>
 * 未配置 App ID/Secret 或未启用时整体降级：不推送卡片、不建立长连接，流程审批不受影响。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "hragent.feishu")
public class FeishuOpenApiProperties {

    /** 是否启用飞书审批卡片推送与回调 */
    private boolean enabled = true;

    /** 飞书开放平台自建应用的 App ID（如 cli_xxx） */
    private String appId = "";

    /** 飞书开放平台自建应用的 App Secret */
    private String appSecret = "";

    /** 开放平台 API 基地址 */
    private String baseUrl = "https://open.feishu.cn/open-apis";

    /** 获取 tenant_access_token 的端点 */
    private String tokenEndpoint = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";

    /** 是否已配置有效凭据 */
    public boolean hasCredentials() {
        return appId != null && !appId.isBlank()
                && appSecret != null && !appSecret.isBlank();
    }
}
