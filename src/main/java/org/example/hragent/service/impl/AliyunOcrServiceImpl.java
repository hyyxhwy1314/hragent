package org.example.hragent.service.impl;

import com.aliyun.ocr20191230.Client;
import com.aliyun.ocr20191230.models.RecognizeCharacterAdvanceRequest;
import com.aliyun.ocr20191230.models.RecognizeCharacterResponseBody;
import com.aliyun.ocr20191230.models.RecognizePdfAdvanceRequest;
import com.aliyun.ocr20191230.models.RecognizePdfResponseBody;
import com.aliyun.teaopenapi.models.Config;
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
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 阿里云 OCR 实现（ocr20191230 SDK v2.0.3）
 *
 * <p>说明：v2.0.3 客户端中 {@code RecognizeResume} 结构化接口不在 SDK 内（属于 VIAPI
 * 独立简历解析服务，通常用另外的 SDK / 异步任务完成）。因此本实现：</p>
 * <ol>
 *   <li>先用 {@code recognizePdfAdvance} 直接识别扫描件 PDF（阿里云原生 PDF OCR）。</li>
 *   <li>图片走 {@code recognizeCharacterAdvance}（流式上传字节，不需要先传到 OSS）。</li>
 *   <li>识别结果拿到全文后，复用与 PDFBox 文本解析完全一致的本地正则链路回填
 *       {@link ResumeParsedData}，保证「文本简历」与「扫描件简历」输出格式严格对齐。</li>
 *   <li>未配置 AK/SK 时返回 {@code null} 并打 WARN，不阻断上传流程。</li>
 * </ol>
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
        try {
            String fn = fileName == null ? "" : fileName.toLowerCase();
            boolean isPdf = fn.endsWith(".pdf")
                    || (contentType != null && contentType.toLowerCase().contains("pdf"));
            if (isPdf) {
                String text = callRecognizePdf(data);
                if (text != null && !text.isBlank()) return text;
                // 降级：PDFBox 渲染 PNG → RecognizeCharacter
                return renderPdfAndRecognize(data);
            }
            if (fn.endsWith(".png") || fn.endsWith(".jpg") || fn.endsWith(".jpeg")
                    || fn.endsWith(".bmp") || fn.endsWith(".webp")
                    || (contentType != null && contentType.startsWith("image/"))) {
                return callRecognizeCharacter(data);
            }
            return null;
        } catch (Exception e) {
            log.warn("阿里云 OCR 通用识别失败 file={}, err={}", fileName, e.getMessage());
            return null;
        }
    }

    @Override
    public ResumeParsedData recognizeResumeStructured(byte[] data, String fileName, String contentType) {
        String text = recognizeRawText(data, fileName, contentType);
        if (text == null || text.isBlank()) return null;
        ResumeParsedData r = parseFromText(text);
        if (allNull(r)) return null;
        return r;
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

    private String callRecognizeCharacter(byte[] imageBytes) throws Exception {
        Client c = getClient();
        if (c == null) return null;
        try (InputStream in = new ByteArrayInputStream(imageBytes)) {
            RecognizeCharacterAdvanceRequest req = new RecognizeCharacterAdvanceRequest()
                    .setImageURLObject(in);
            RecognizeCharacterResponseBody body = c.recognizeCharacterAdvance(req, null).getBody();
            return collectCharacterLines(body);
        }
    }

    private static String collectCharacterLines(RecognizeCharacterResponseBody body) throws Exception {
        if (body == null) return null;
        Object data = body.getData();
        if (data == null) return null;
        Object results = invokeGetter(data, "getResults");
        if (!(results instanceof List<?> list)) return null;
        StringBuilder sb = new StringBuilder();
        for (Object r : list) {
            Object t = invokeGetter(r, "getText");
            if (t instanceof String s && !s.isBlank()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(s);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private String callRecognizePdf(byte[] pdfBytes) {
        Client c = getClient();
        if (c == null) return null;
        try (InputStream in = new ByteArrayInputStream(pdfBytes)) {
            RecognizePdfAdvanceRequest req = new RecognizePdfAdvanceRequest()
                    .setFileURLObject(in);
            RecognizePdfResponseBody body = c.recognizePdfAdvance(req, null).getBody();
            return collectPdfLines(body);
        } catch (Exception e) {
            log.debug("阿里云 RecognizePdf 失败，降级本地 PDF 渲染: {}", e.getMessage());
            return null;
        }
    }

    private static String collectPdfLines(RecognizePdfResponseBody body) throws Exception {
        if (body == null) return null;
        Object data = body.getData();
        if (data == null) return null;
        Object words = invokeGetter(data, "getWordsInfo");
        if (!(words instanceof List<?> list)) return null;
        StringBuilder sb = new StringBuilder();
        for (Object w : list) {
            Object s = invokeGetter(w, "getWord");
            if (s instanceof String word && !word.isBlank()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(word);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    // ========================= 扫描件 PDF 兜底渲染 =========================

    private String renderPdfAndRecognize(byte[] pdfBytes) {
        try (var doc = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pages = Math.min(doc.getNumberOfPages(), props.getPdfMaxPages());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pages; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, props.getRenderDpi(), ImageType.RGB);
                ByteArrayOutputStream baos = new ByteArrayOutputStream(1024 * 256);
                ImageIO.write(img, "png", baos);
                String page = callRecognizeCharacter(baos.toByteArray());
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

    private static Object invokeGetter(Object obj, String method) throws ReflectiveOperationException {
        Method m = findMethod(obj.getClass(), method);
        if (m == null) return null;
        m.setAccessible(true);
        return m.invoke(obj);
    }

    private static Method findMethod(Class<?> c, String name) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
        }
        return null;
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
