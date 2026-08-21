package org.example.hragent.service.impl;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeAllTextRequest;
import com.aliyun.ocr_api20210707.models.RecognizeAllTextResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.example.hragent.config.AliyunOcrProperties;
import org.example.hragent.service.AliyunOcrService;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 阿里云 OCR 实现（ocr_api20210707 SDK，对应控制台「OCR统一识别」服务，API 版本 2021-07-07）。
 *
 * <p>统一走 {@code RecognizeAllText} 接口，通过 {@code Type} 参数切换能力（默认 Advanced=通用文字识别高精版）：
 * <ol>
 *   <li>图片（PNG/JPG/JPEG/BMP/GIF/TIFF/WebP）→ 直接传字节流识别，返回 data.content 全文。</li>
 *   <li>扫描件 PDF → 官方统一识别对 PDF「仅识别第一页」，为保证多页简历字段覆盖，
 *       先用 PDFBox 按页渲染为 PNG，再逐页调用 RecognizeAllText，最后拼接全文。</li>
 * </ol>
 * 只返回识别出的纯文本，结构化字段抽取交由后续 AI 分析完成。
 *
 * <p>未配置 AK/SK 时返回 {@code null} 并打 WARN，不阻断上传流程。
 * {@code RuntimeOptions} 开启自动重试并放大超时（覆盖「解析失败重试」诉求）；
 * 识别失败/异常时以 ERROR 级别记录 code/message/requestId（覆盖「异常告警」诉求）。
 */
@Slf4j
@Service
public class AliyunOcrServiceImpl implements AliyunOcrService {

    private final AliyunOcrProperties props;
    private volatile Client client;
    private volatile boolean clientResolved = false;

    public AliyunOcrServiceImpl(AliyunOcrProperties props) {
        this.props = props;
    }

    // ========================= 对外接口 =========================

    @Override
    public String recognizeRawText(byte[] data, String fileName, String contentType) {
        if (!props.isEnabled()) {
            log.warn("阿里云 OCR 未配置 ak/sk，跳过文本识别 fileName={}", fileName);
            return null;
        }
        if (data == null || data.length == 0) return null;

        String fn = fileName == null ? "" : fileName.toLowerCase();
        boolean isPdf = fn.endsWith(".pdf")
                || (contentType != null && contentType.toLowerCase().contains("pdf"));
        String text;
        if (isPdf) {
            // 统一识别对 PDF 仅识别第一页，扫描件多页 PDF 走「逐页渲染 + OCR」保证字段覆盖
            text = renderPdfAndRecognize(data);
        } else if (fn.endsWith(".png") || fn.endsWith(".jpg") || fn.endsWith(".jpeg")
                || fn.endsWith(".bmp") || fn.endsWith(".gif") || fn.endsWith(".tif")
                || fn.endsWith(".tiff") || fn.endsWith(".webp")
                || (contentType != null && contentType.startsWith("image/"))) {
            text = callRecognizeAllText(data);
        } else {
            return null;
        }

        if (text != null && !text.isBlank()) {
            log.info("OCR 识别成功 fileName={}, 字符数={}", fileName, text.length());
        }
        return text;
    }

    /**
     * 调试用：直接调用 RecognizeAllText，把阿里云返回的完整字段（code/message/requestId/
     * subCode/content/是否报错）原样塞进 Map 返回，方便定位"识别成功但 content 为空"的根因。
     */
    @Override
    public Map<String, Object> recognizeAllTextDebug(byte[] fileBytes) {
        Map<String, Object> diag = new LinkedHashMap<>();
        diag.put("ocrEnabled", props.isEnabled());
        diag.put("type", props.getType());
        diag.put("endpoint", props.getEndpoint());
        if (!props.isEnabled() || fileBytes == null || fileBytes.length == 0) {
            diag.put("skipped", true);
            return diag;
        }
        Client c = getClient();
        diag.put("clientReady", c != null);
        if (c == null) return diag;
        try (ByteArrayInputStream in = new ByteArrayInputStream(fileBytes)) {
            RecognizeAllTextRequest req = new RecognizeAllTextRequest()
                    .setBody(in)
                    .setType(props.getType());
            RuntimeOptions ro = new RuntimeOptions()
                    .setAutoretry(true)
                    .setReadTimeout(30000)
                    .setConnectTimeout(10000);
            RecognizeAllTextResponseBody body = c.recognizeAllTextWithOptions(req, ro).getBody();
            diag.put("bodyNull", body == null);
            if (body == null) return diag;
            diag.put("code", body.getCode());
            diag.put("message", body.getMessage());
            diag.put("requestId", body.getRequestId());
            diag.put("dataNull", body.getData() == null);
            if (body.getData() != null) {
                String content = body.getData().getContent();
                diag.put("contentLength", content == null ? 0 : content.length());
                diag.put("contentPreview", content == null ? null
                        : content.substring(0, Math.min(500, content.length())));
            }
            // 把 data 对象的 subCode 等常见失败字段也尝试带出来
            try {
                java.lang.reflect.Method m = body.getData() == null ? null
                        : body.getData().getClass().getMethod("getSubCode");
                if (m != null) diag.put("subCode", m.invoke(body.getData()));
            } catch (Exception ignored) {
                // 不同 SDK 版本字段不同，忽略
            }
        } catch (Exception e) {
            diag.put("exception", e.getClass().getName() + ": " + e.getMessage());
        }
        return diag;
    }

    // ========================= SDK 调用 =========================

    private Client getClient() {
        if (clientResolved) return client;
        synchronized (this) {
            if (clientResolved) return client;
            try {
                Config cfg = new Config()
                        .setAccessKeyId(props.getAccessKeyId())
                        .setAccessKeySecret(props.getAccessKeySecret())
                        .setRegionId(props.getRegionId())
                        .setEndpoint(props.getEndpoint());
                client = new Client(cfg);
            } catch (Exception e) {
                log.error("初始化阿里云 OCR 客户端失败: {}", e.getMessage());
                client = null;
            }
            clientResolved = true;
            return client;
        }
    }

    /**
     * 调用 RecognizeAllText（通用文字识别高精版），开启自动重试 + 放大超时。
     * 识别成功返回 data.content 全文；失败返回 null 并记录告警日志。
     */
    private String callRecognizeAllText(byte[] fileBytes) {
        Client c = getClient();
        if (c == null) return null;
        try (ByteArrayInputStream in = new ByteArrayInputStream(fileBytes)) {
            RecognizeAllTextRequest req = new RecognizeAllTextRequest()
                    .setBody(in)
                    .setType(props.getType());
            // autoretry 覆盖瞬时错误（限流/网络抖动）；readTimeout 放大到 30s 兜住大图/高 DPI 渲染页
            RuntimeOptions ro = new RuntimeOptions()
                    .setAutoretry(true)
                    .setReadTimeout(30000)
                    .setConnectTimeout(10000);
            RecognizeAllTextResponseBody body = c.recognizeAllTextWithOptions(req, ro).getBody();
            if (body == null) return null;
            // 统一识别成功时不返回 code；失败时 code 非空（错误码字符串）
            String code = body.getCode();
            if (code != null && !code.isBlank() && !"0".equals(code)) {
                log.error("阿里云 OCR 识别失败 code={}, message={}, requestId={}, 字节数={}",
                        code, body.getMessage(), body.getRequestId(), fileBytes.length);
                return null;
            }
            String content = (body.getData() == null) ? null : body.getData().getContent();
            return (content == null || content.isBlank()) ? null : content;
        } catch (Exception e) {
            log.error("阿里云 RecognizeAllText 调用异常: {}", e.getMessage());
            return null;
        }
    }

    // ========================= 扫描件 PDF 逐页渲染 =========================

    private String renderPdfAndRecognize(byte[] pdfBytes) {
        try (var doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pages = Math.min(doc.getNumberOfPages(), props.getPdfMaxPages());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pages; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, props.getRenderDpi(), ImageType.RGB);
                ByteArrayOutputStream baos = new ByteArrayOutputStream(1024 * 256);
                ImageIO.write(img, "png", baos);
                String page = callRecognizeAllText(baos.toByteArray());
                if (page != null && !page.isBlank()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(page);
                }
            }
            return sb.isEmpty() ? null : sb.toString();
        } catch (Exception e) {
            log.warn("PDF→PNG 渲染识别失败: {}", e.getMessage());
            return null;
        }
    }
}
