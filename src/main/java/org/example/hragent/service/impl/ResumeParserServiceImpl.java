package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.hragent.service.ResumeParserService;
import org.example.hragent.vo.ResumeParsedData;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简历解析服务实现
 * 使用 PDFBox 提取 PDF 文本，配合正则识别常见字段
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

    @Override
    public ResumeParsedData parse(byte[] data, String fileName, String contentType) {
        ResumeParsedData result = new ResumeParsedData();
        if (data == null || data.length == 0) {
            log.warn("简历解析入参为空 fileName={}", fileName);
            return result;
        }
        String text = extractText(data, fileName, contentType);
        if (text == null || text.isBlank()) {
            // 文本提取失败（如扫描件 PDF），仍尝试从文件名提取姓名
            String nameFromName = guessNameFromFileName(fileName);
            result.setResumeName(nameFromName);
            log.info("简历文本提取为空，仅从文件名识别姓名 fileName={}, name={}", fileName, nameFromName);
            return result;
        }
        result.setRawText(text);
        result.setPhone(first(PHONE, text));
        result.setEmail(first(EMAIL, text));
        result.setResumeName(orDefault(first(NAME_LABEL, text), guessNameFromFileName(fileName)));
        result.setMajor(first(MAJOR_LABEL, text));
        result.setExpectPosition(first(EXPECT_POSITION, text));
        result.setExpectCity(first(EXPECT_CITY, text));
        result.setWorkYears(parseInt(first(WORK_YEARS, text), first(WORK_YEARS_LABEL, text)));
        result.setEducation(guessEducation(text));
        if (result.getSchool() == null) {
            result.setSchool(first(SCHOOL, text));
        }
        log.info("简历解析完成 fileName={}, name={}, phone={}, email={}, school={}",
                fileName, result.getResumeName(), result.getPhone(), result.getEmail(), result.getSchool());
        return result;
    }

    /** 提取文件文本，仅支持 PDF；其他类型返回 null */
    private String extractText(byte[] data, String fileName, String contentType) {
        boolean isPdf = (fileName != null && fileName.toLowerCase().endsWith(".pdf"))
                || (contentType != null && contentType.toLowerCase().contains("pdf"));
        if (!isPdf) {
            log.info("非 PDF 文件，跳过文本提取: {}", fileName);
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
            log.warn("PDF 文本提取失败 file={}, err={}", fileName, e.getMessage());
            return null;
        }
    }

    /** 返回首个正则匹配：有捕获组取组1，否则取整体匹配；无匹配返回 null */
    private String first(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (!m.find()) {
            return null;
        }
        String g = m.groupCount() >= 1 ? m.group(1) : m.group(0);
        return g == null ? null : g.trim();
    }

    private String orDefault(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
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

    /** 学历识别：取文中最高的学历 */
    private Integer guessEducation(String text) {
        if (text.contains("博士")) return 4;
        if (text.contains("硕士") || text.contains("研究生")) return 3;
        if (text.contains("本科") || text.contains("学士")) return 2;
        if (text.contains("大专") || text.contains("专科")) return 1;
        return null;
    }

    /** 文件名 "姓名-岗位.pdf" 启发式提取姓名 */
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
            // 去除常见前缀如"简历"
            if (first.length() >= 2 && first.length() <= 4
                    && first.matches("[\\u4e00-\\u9fa5]+") && !first.contains("简历")) {
                return first;
            }
        }
        return null;
    }
}
