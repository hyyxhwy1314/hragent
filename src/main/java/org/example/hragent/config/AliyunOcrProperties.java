package org.example.hragent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OCR 配置
 * 未配置 AK/SK 时，服务会自动降级（不抛异常，仅在扫描件路径打 WARN 日志）。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.ocr")
public class AliyunOcrProperties {

    /** AccessKey ID；为空表示关闭阿里云 OCR 调用 */
    private String accessKeyId = "";

    /** AccessKey Secret */
    private String accessKeySecret = "";

    /** 接入点，默认走上海通用识别域 */
    private String endpoint = "ocr.cn-shanghai.aliyuncs.com";

    /** 区域 ID */
    private String regionId = "cn-shanghai";

    /** 扫描件 PDF 渲染最大页数（防止超大 PDF 把内存打爆） */
    private int pdfMaxPages = 5;

    /** PDF 渲染 DPI（OCR 精度） */
    private int renderDpi = 240;

    /** 是否已配置有效凭据 */
    public boolean isEnabled() {
        return accessKeyId != null && !accessKeyId.isBlank()
                && accessKeySecret != null && !accessKeySecret.isBlank();
    }
}
