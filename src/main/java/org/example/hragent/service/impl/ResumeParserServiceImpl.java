package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.hragent.service.AliyunOcrService;
import org.example.hragent.service.ResumeParserService;
import org.example.hragent.vo.ResumeParsedData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历解析服务实现
 * <p>
 * 链路分层（统一输出 {@link ResumeParsedData}，保证 text 型简历与扫描件简历字段完全对齐）：
 * <ol>
 *   <li>优先尝试阿里云 RecognizeResume 结构化识别（准确率高，≥85% 验收依赖它）</li>
 *   <li>结构化未命中 → 走 PDFBox 抽取纯文本（文本型 PDF 很快、免费）</li>
 *   <li>PDFBox 抽不出（扫描件/图片） → 再 fallback 到 OCR RecognizeGeneral + 本地正则</li>
 *   <li>最后再结合文件名兜底姓名（即便前几层都失败，至少不会完全空白）</li>
 * </ol>
 */
@Slf4j
@Service
public class ResumeParserServiceImpl implements ResumeParserService {

    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern NAME_LABEL = Pattern.compile("姓名[\\s:：]*([\\u4e00-\\u9fa5]{2,4})");
    private static final Pattern MAJOR_LABEL = Pattern.compile("专业[\\s:：]*([\\u4e00-\\u9fa5A-Za-z()（）]+)");
    private static final Pattern EXPECT_POSITION = Pattern.compile("(?:求职意向|期望职位|意向岗位|应聘职位)[\\s:：]*([^\\n\\r,，;；|]{2,40})");
    private static final Pattern EXPECT_CITY = Pattern.compile("(?:期望城市|意向城市|期望地点|意向地点)[\\s:：]*([^\\n\\r,，;；|]{2,20})");
    private static final Pattern WORK_YEARS = Pattern.compile("(\\d+)\\s*年(?:工作经验|工作经历|经验)");
    private static final Pattern WORK_YEARS_LABEL = Pattern.compile("工作年限[\\s:：]*(\\d+)");
    private static final Pattern SCHOOL = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z()（）]{2,30}(?:大学|学院|UNIVERSITY|University))");

    @Autowired
    @Lazy
    private AliyunOcrService aliyunOcrService;

    @Override
    public ResumeParsedData parse(byte[] data, String fileName, String contentType) {
        ResumeParsedData result = new ResumeParsedData();
        if (data == null || data.length == 0) {
            log.warn("简历解析入参为空 fileName={}", fileName);
            return result;
        }

        // Layer 1: 结构化 OCR（阿里云 RecognizeResume）—— 扫描件 / 图片 / 文本型 PDF 都能吃，
        // 命中的话字段质量通常比本地正则高。
        ResumeParsedData structured = tryOcrStructured(data, fileName, contentType);
        if (structured != null) {
            mergeInto(result, structured);
        }

        // Layer 2: PDFBox 文本提取（文本型 PDF 不需要消耗 OCR 额度）
        String pdfText = extractPdfBoxText(data, fileName, contentType);
        if (pdfText != null && !pdfText.isBlank()) {
            ResumeParsedData regexed = parseFromText(pdfText);
            mergeInto(result, regexed);
            // 保证 rawText 里最终保留 PDF 原文（OCR 原文可能断行不连贯）
            if (result.getRawText() == null || pdfText.length() > result.getRawText().length()) {
                result.setRawText(pdfText);
            }
        }

        // Layer 3: PDFBox 提取空（典型扫描件 PDF）或图片文件 → OCR 通用识别 + 正则
        if (structured == null && (pdfText == null || pdfText.isBlank())) {
            String ocrText = (aliyunOcrService == null) ? null
                    : aliyunOcrService.recognizeRawText(data, fileName, contentType);
            if (ocrText != null && !ocrText.isBlank()) {
                ResumeParsedData regexed = parseFromText(ocrText);
                mergeInto(result, regexed);
                if (result.getRawText() == null) result.setRawText(ocrText);
                log.info("扫描件简历 OCR 填充字段 fileName={}, name={}, phone={}",
                        fileName, result.getResumeName(), result.getPhone());
            }
        }

        // Layer 4: 即便所有解析都失败，仍用文件名做一次最小兜底，前端至少能显示个姓名占位
        if (result.getResumeName() == null || result.getResumeName().isBlank()) {
            result.setResumeName(guessNameFromFileName(fileName));
        }

        log.info("简历解析完成 fileName={}, name={}, phone={}, email={}, school={}",
                fileName, result.getResumeName(), result.getPhone(), result.getEmail(), result.getSchool());
        return result;
    }

    // ============== 分层辅助 ==============

    private ResumeParsedData tryOcrStructured(byte[] data, String fileName, String contentType) {
        if (aliyunOcrService == null) return null;
        try {
            return aliyunOcrService.recognizeResumeStructured(data, fileName, contentType);
        } catch (Exception e) {
            log.warn("结构化 OCR 失败，继续走本地解析 file={}, err={}", fileName, e.getMessage());
            return null;
        }
    }

    private String extractPdfBoxText(byte[] data, String fileName, String contentType) {
        boolean isPdf = (fileName != null && fileName.toLowerCase().endsWith(".pdf"))
                || (contentType != null && contentType.toLowerCase().contains("pdf"));
        if (!isPdf) {
            return null;
        }
        try (PDDocument doc = Loader.loadPDF(data)) {
            if (doc.isEncrypted()) {
                doc.setAllSecurityToBeRemoved(true);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (Exception e) {
            log.warn("PDFBox 文本提取失败 file={}, err={}", fileName, e.getMessage());
            return null;
        }
    }

    private ResumeParsedData parseFromText(String text) {
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

    /** src 里非空字段覆盖/合并到 dst（空字段不覆盖，允许 OCR 与 PDFBox 互补） */
    private static void mergeInto(ResumeParsedData dst, ResumeParsedData src) {
        if (src == null) return;
        if (dst.getResumeName() == null || dst.getResumeName().isBlank()) dst.setResumeName(src.getResumeName());
        if (dst.getPhone() == null || dst.getPhone().isBlank()) dst.setPhone(src.getPhone());
        if (dst.getEmail() == null || dst.getEmail().isBlank()) dst.setEmail(src.getEmail());
        if (dst.getSchool() == null || dst.getSchool().isBlank()) dst.setSchool(src.getSchool());
        if (dst.getMajor() == null || dst.getMajor().isBlank()) dst.setMajor(src.getMajor());
        if (dst.getExpectPosition() == null || dst.getExpectPosition().isBlank()) dst.setExpectPosition(src.getExpectPosition());
        if (dst.getExpectCity() == null || dst.getExpectCity().isBlank()) dst.setExpectCity(src.getExpectCity());
        if (dst.getWorkYears() == null) dst.setWorkYears(src.getWorkYears());
        if (dst.getEducation() == null) dst.setEducation(src.getEducation());
        if (dst.getRawText() == null) dst.setRawText(src.getRawText());
    }

    // ============== 原有正则/兜底工具 ==============

    private String first(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (!m.find()) {
            return null;
        }
        String g = m.groupCount() >= 1 ? m.group(1) : m.group(0);
        return g == null ? null : g.trim();
    }

    private Integer parseInt(String... values) {
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

    private Integer guessEducation(String text) {
        if (text.contains("博士")) return 4;
        if (text.contains("硕士") || text.contains("研究生")) return 3;
        if (text.contains("本科") || text.contains("学士")) return 2;
        if (text.contains("大专") || text.contains("专科")) return 1;
        return null;
    }

    private String guessNameFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String base = fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String[] parts = base.split("[-_—\\s]+");
        if (parts.length > 0) {
            String first = parts[0].trim();
            if (first.length() >= 2 && first.length() <= 4
                    && first.matches("[\\u4e00-\\u9fa5]+") && !first.contains("简历")) {
                return first;
            }
        }
        return null;
    }
}

