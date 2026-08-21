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
import org.example.hragent.vo.ResumeParsedData;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阿里云 OCR 实现（ocr_api20210707 SDK，对应控制台「OCR统一识别」服务，API 版本 2021-07-07）。
 *
 * <p>统一走 {@code RecognizeAllText} 接口，通过 {@code Type} 参数切换能力（默认 Advanced=通用文字识别高精版）：
 * <ol>
 *   <li>图片（PNG/JPG/JPEG/BMP/GIF/TIFF/WebP）→ 直接传字节流识别，返回 data.content 全文。</li>
 *   <li>扫描件 PDF → 官方统一识别对 PDF「仅识别第一页」，为保证多页简历字段覆盖，
 *       先用 PDFBox 按页渲染为 PNG，再逐页调用 RecognizeAllText，最后拼接全文。</li>
 * </ol>
 * 识别出全文后复用与 {@link org.example.hragent.service.impl.ResumeParserServiceImpl} 一致的正则链路回填
 * {@link ResumeParsedData}，保证「文本型简历」与「扫描件简历」输出格式严格对齐。
 *
 * <p>未配置 AK/SK 时返回 {@code null} 并打 WARN，不阻断上传流程。
 * {@code RuntimeOptions} 开启自动重试并放大超时（覆盖「解析失败重试」诉求）；
 * 识别失败/异常时以 ERROR 级别记录 code/message/requestId（覆盖「异常告警」诉求）。
 */
@Slf4j
@Service
public class AliyunOcrServiceImpl implements AliyunOcrService {

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern NAME_LABEL = Pattern.compile("姓名[\\s:：]*([\\u4e00-\\u9fa5]{2,4})");
    private static final Pattern MAJOR_LABEL = Pattern.compile("专业[\\s:：]*([\\u4e00-\\u9fa5A-Za-z()（）]+)");
    private static final Pattern EXPECT_POSITION = Pattern.compile("(?:求职意向|期望职位|意向岗位|应聘职位)[\\s:：]*([^\\n\\r,，;；|]{2,40})");
    private static final Pattern EXPECT_CITY = Pattern.compile("(?:期望城市|意向城市|期望地点|意向地点)[\\s:：]*([^\\n\\r,，;；|]{2,20})");
    private static final Pattern WORK_YEARS = Pattern.compile("(\\d+)\\s*年(?:工作经验|工作经历|经验)");
    private static final Pattern WORK_YEARS_LABEL = Pattern.compile("工作年限[\\s:：]*(\\d+)");
    private static final Pattern SCHOOL = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z()（）]{2,30}(?:大学|学院|UNIVERSITY|University))");

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
        if (isPdf) {
            // 统一识别对 PDF 仅识别第一页，扫描件多页 PDF 走「逐页渲染 + OCR」保证字段覆盖
            return renderPdfAndRecognize(data);
        }
        if (fn.endsWith(".png") || fn.endsWith(".jpg") || fn.endsWith(".jpeg")
                || fn.endsWith(".bmp") || fn.endsWith(".gif") || fn.endsWith(".tif")
                || fn.endsWith(".tiff") || fn.endsWith(".webp")
                || (contentType != null && contentType.startsWith("image/"))) {
            return callRecognizeAllText(data);
        }
        return null;
    }

    @Override
    public ResumeParsedData recognizeResumeStructured(byte[] data, String fileName, String contentType) {
        String text = recognizeRawText(data, fileName, contentType);
        if (text == null || text.isBlank()) return null;
        ResumeParsedData r = parseFromText(text);
        if (allNull(r)) return null;
        return r;
    }

    /**
     * 调试用：直接调用 RecognizeAllText，把阿里云返回的完整字段（code/message/requestId/
     * subCode/content/是否报错）原样塞进 Map 返回，方便定位"识别成功但 content 为空"的根因。
     */
    @Override
    public java.util.Map<String, Object> recognizeAllTextDebug(byte[] fileBytes) {
        java.util.Map<String, Object> diag = new java.util.LinkedHashMap<>();
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

    // ========================= 正则解析（与 ResumeParserServiceImpl 保持一致，保证输出对齐） =========================

    private static ResumeParsedData parseFromText(String text) {
        ResumeParsedData r = new ResumeParsedData();
        if (text == null || text.isBlank()) return r;
        r.setRawText(text);
        r.setPhone(first(PHONE, text));
        r.setEmail(first(EMAIL, text));
        r.setResumeName(first(NAME_LABEL, text));
        r.setMajor(first(MAJOR_LABEL, text));
        r.setExpectPosition(first(EXPECT_POSITION, text));
        r.setExpectCity(first(EXPECT_CITY, text));
        r.setWorkYears(parseInt(first(WORK_YEARS, text), first(WORK_YEARS_LABEL, text)));
        r.setEducation(guessEducation(text));
        if (r.getSchool() == null) r.setSchool(first(SCHOOL, text));
        return r;
    }

    private static boolean allNull(ResumeParsedData r) {
        return r.getResumeName() == null && r.getPhone() == null && r.getEmail() == null
                && r.getSchool() == null && r.getMajor() == null
                && r.getExpectPosition() == null && r.getExpectCity() == null
                && r.getWorkYears() == null && r.getEducation() == null;
    }

    private static String first(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (!m.find()) return null;
        String g = m.groupCount() >= 1 ? m.group(1) : m.group(0);
        return g == null ? null : g.trim();
    }

    private static Integer parseInt(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                try {
                    return Integer.valueOf(v.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private static Integer guessEducation(String text) {
        if (text.contains("博士")) return 4;
        if (text.contains("硕士") || text.contains("研究生")) return 3;
        if (text.contains("本科") || text.contains("学士")) return 2;
        if (text.contains("大专") || text.contains("专科")) return 1;
        return null;
    }
}
