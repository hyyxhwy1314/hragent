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

/**
 * 简历解析服务实现
 * <p>
 * 只负责"提取文本"，不做字段抽取。链路：
 * <ol>
 *   <li>PDF 文件 → 先用 PDFBox 抽取纯文本（文本型 PDF 很快、免费）</li>
 *   <li>PDFBox 抽不出（扫描件 PDF）或图片文件 → 走阿里云 OCR 识别</li>
 * </ol>
 * 提取出的全文写入 {@link ResumeParsedData#getRawText()}，结构化字段抽取交由后续 AI 分析完成。
 */
@Slf4j
@Service
public class ResumeParserServiceImpl implements ResumeParserService {

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

        // Layer 1: PDFBox 文本提取（文本型 PDF 不消耗 OCR 额度）
        String text = extractPdfBoxText(data, fileName, contentType);

        // Layer 2: PDFBox 提取空（典型扫描件 PDF）或图片文件 → OCR 通用识别
        if ((text == null || text.isBlank()) && aliyunOcrService != null) {
            try {
                text = aliyunOcrService.recognizeRawText(data, fileName, contentType);
            } catch (Exception e) {
                log.warn("OCR 识别异常 file={}, err={}", fileName, e.getMessage());
            }
        }

        if (text != null && !text.isBlank()) {
            result.setRawText(text);
            log.info("简历文本提取完成 fileName={}, 字符数={}", fileName, text.length());
        } else {
            log.warn("简历文本提取失败 fileName={}", fileName);
        }
        return result;
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
}
