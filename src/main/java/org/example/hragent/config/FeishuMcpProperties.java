package org.example.hragent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 飞书官方远程 MCP 服务配置（远程调用模式）
 * <p>
 * 协议说明：所有请求统一 POST 到 {@code endpoint}，JSON-RPC 2.0 请求体，
 * 通过 {@code X-Lark-MCP-TAT}（应用身份令牌）鉴权，并在
 * {@code X-Lark-MCP-Allowed-Tools} 中声明本次连接允许调用的工具。
 * 详细接入文档：https://open.feishu.cn/document/mcp_open_tools/developers-call-remote-mcp-server
 * <p>
 * 未配置 App ID/Secret 时，服务自动降级（不发起任何网络请求，AI 仅使用本地 HR 工具）。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "hragent.mcp.feishu")
public class FeishuMcpProperties {

    /** 是否启用飞书 MCP */
    private boolean enabled = true;

    /** 飞书开放平台自建应用的 App ID（如 cli_xxx） */
    private String appId = "";

    /** 飞书开放平台自建应用的 App Secret */
    private String appSecret = "";

    /** 飞书远程 MCP 服务端点 */
    private String endpoint = "https://mcp.feishu.cn/mcp";

    /** 获取 tenant_access_token 的端点（内部应用，app_id + app_secret 直接换 TAT） */
    private String tokenEndpoint = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";

    /** 本次连接允许调用的工具列表（逗号分隔）。
     *  注意：search-doc/search-user 仅支持用户身份（UAT），走应用身份（TAT）时应排除 */
    private String allowedTools = "create-doc,fetch-doc,update-doc,list-docs,get-comments,add-comments,get-user,fetch-file";

    /** 是否已配置有效凭据 */
    public boolean hasCredentials() {
        return appId != null && !appId.isBlank()
                && appSecret != null && !appSecret.isBlank();
    }
}
