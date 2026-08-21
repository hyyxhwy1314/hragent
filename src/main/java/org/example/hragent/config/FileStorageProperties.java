package org.example.hragent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对象存储配置属性
 * 采用通用命名（FileStorage），不绑定具体厂商
 */
@Data
@Component
@ConfigurationProperties(prefix = "cos.client")
public class FileStorageProperties {

    /** 访问密钥ID */
    private String accessKey;

    /** 访问密钥 */
    private String secretKey;

    /** 地域 */
    private String region;

    /** 存储桶名称 */
    private String bucket;

    /** 文件存储目录前缀，如 resume/ */
    private String prefix = "";

    /** 预签名URL有效期（秒） */
    private Long expire = 3600L;
}
