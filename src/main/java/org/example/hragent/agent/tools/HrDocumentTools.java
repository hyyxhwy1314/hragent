package org.example.hragent.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.example.hragent.service.FileStorageService;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文档生成与沟通话术工具：Excel 表格、Word 文档、内部通知话术。
 */
@Component
public class HrDocumentTools implements HrToolSet {

    /** 沟通场景关键词 → 话术标题 */
    private static final Map<String, String> SCENARIO_LABELS = Map.of(
            "放假", "放假通知",
            "离职", "离职公告",
            "培训", "培训通知",
            "转正", "转正公告",
            "生日", "生日祝福",
            "提醒", "温馨提示",
            "招聘", "招聘进展"
    );

    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public HrDocumentTools(FileStorageService fileStorageService, ObjectMapper objectMapper) {
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成 Excel 表格文件（如员工花名册、工资表、培训记录等），返回文件下载链接。
     * <p>参数需包含 JSON：{"title":"表格标题","headers":["列1","列2"],"rows":[["值1","值2"],...]}
     */
    @Tool("生成 Excel 表格文件（如员工花名册、工资表、培训记录等），参数需包含 JSON：{\"title\":\"表格标题\",\"headers\":[\"列名\"],\"rows\":[[\"单元格值\"]]}，返回文件下载链接")
    public String generateExcelFile(String query) {
        try {
            Map<String, Object> spec = parseSpec(query);
            String title = str(spec, "title", "数据表");
            List<String> headers = list(spec.get("headers"));
            List<List<String>> rows = rows(spec.get("rows"));
            String url = uploadBytes(buildXlsx(title, headers, rows), "table.xlsx");
            return "已生成 Excel 文件【" + title + "】，共 " + rows.size() + " 行数据，下载链接（1小时内有效）：" + url;
        } catch (Exception e) {
            return "生成 Excel 失败：" + e.getMessage()
                    + "。请提供 JSON 参数，格式：{\"title\":\"标题\",\"headers\":[\"列名\"],\"rows\":[[\"值\"]]}";
        }
    }

    /**
     * 生成 Word 文档（如录用通知书、离职证明、在职证明、入职材料等），返回文件下载链接。
     * <p>参数需包含 JSON：{"title":"文档标题","paragraphs":["正文段落"],"table":{"headers":[...],"rows":[[...]]}}，table 可选
     */
    @Tool("生成 Word 文档（如录用通知书、离职证明、在职证明、入职材料等），参数需包含 JSON：{\"title\":\"文档标题\",\"paragraphs\":[\"正文段落\"],\"table\":{\"headers\":[\"列名\"],\"rows\":[[\"值\"]]}}，table 可选，返回文件下载链接")
    public String generateWordDocument(String query) {
        try {
            Map<String, Object> spec = parseSpec(query);
            String title = str(spec, "title", "文档");
            List<String> paragraphs = list(spec.get("paragraphs"));
            String url = uploadBytes(buildDocx(title, paragraphs, spec), "document.docx");
            return "已生成 Word 文档【" + title + "】，下载链接（1小时内有效）：" + url;
        } catch (Exception e) {
            return "生成 Word 文档失败：" + e.getMessage()
                    + "。请提供 JSON 参数，格式：{\"title\":\"标题\",\"paragraphs\":[\"正文\"],\"table\":{\"headers\":[],\"rows\":[]}}";
        }
    }

    /** 生成内部沟通话术（如放假通知、离职公告、培训通知、转正公告等） */
    @Tool("生成内部沟通话术（如放假通知、离职公告、培训通知、转正公告、生日祝福等），参数描述场景、接收对象与要点，返回话术文本")
    public String composeInternalNotice(String query) {
        if (query == null || query.isBlank()) {
            return "请描述需要生成话术的场景（如放假、离职、培训）与要点";
        }
        String scenario = scenarioOf(query);
        return String.format(
                "【%s】\n各位同事：\n大家好！现就「%s」事项通知如下：\n1. 相关安排与具体要求：%s\n2. 如有疑问，请联系 HR 团队。\n感谢大家的配合与支持！\n\n—— HR 团队",
                scenario, scenario, compact(query, 80));
    }

    /** 识别沟通场景，默认"通知" */
    private String scenarioOf(String query) {
        for (Map.Entry<String, String> entry : SCENARIO_LABELS.entrySet()) {
            if (query.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "通知";
    }

    /** 截断文本到指定长度，保留关键信息 */
    private String compact(String text, int max) {
        String s = text.replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    /** 从查询文本中提取 JSON 对象 */
    private Map<String, Object> parseSpec(String query) throws Exception {
        int start = query.indexOf('{');
        int end = query.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("参数需包含 JSON 数据");
        }
        return objectMapper.readValue(query.substring(start, end + 1), new TypeReference<Map<String, Object>>() {});
    }

    private String str(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : v.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof List) {
            for (Object item : (List<Object>) v) {
                out.add(item == null ? "" : item.toString());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> rows(Object v) {
        List<List<String>> out = new ArrayList<>();
        if (v instanceof List) {
            for (Object row : (List<Object>) v) {
                out.add(list(row));
            }
        }
        return out;
    }

    /** 构建 xlsx 字节流：表头加粗居中，列宽自适应 */
    private byte[] buildXlsx(String title, List<String> headers, List<List<String>> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet(safeSheetName(title));
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            int headerCount = Math.max(headers.size(), 1);
            Row headerRow = sheet.createRow(0);
            for (int c = 0; c < headerCount; c++) {
                Cell cell = headerRow.createCell(c);
                String text = c < headers.size() && headers.get(c) != null ? headers.get(c) : "";
                cell.setCellValue(text);
                cell.setCellStyle(headerStyle);
                int width = Math.min(40, Math.max(12, text.length() + 4));
                sheet.setColumnWidth(c, width * 256);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<String> data = rows.get(r);
                for (int c = 0; c < headerCount; c++) {
                    String v = c < data.size() && data.get(c) != null ? data.get(c) : "";
                    row.createCell(c).setCellValue(v);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 构建 docx 字节流：标题 + 正文段落 + 可选表格 */
    private byte[] buildDocx(String title, List<String> paragraphs, Map<String, Object> spec) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titlePara.createRun();
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            for (String p : paragraphs) {
                XWPFParagraph para = doc.createParagraph();
                para.createRun().setText(p == null ? "" : p);
            }
            Object tableObj = spec.get("table");
            if (tableObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> table = (Map<String, Object>) tableObj;
                List<String> tHeaders = list(table.get("headers"));
                List<List<String>> tRows = rows(table.get("rows"));
                int colCount = Math.max(tHeaders.size(), 1);
                org.apache.poi.xwpf.usermodel.XWPFTable tableNode =
                        doc.createTable(Math.max(tRows.size() + 1, 1), colCount);
                for (int c = 0; c < colCount; c++) {
                    String h = c < tHeaders.size() && tHeaders.get(c) != null ? tHeaders.get(c) : "";
                    tableNode.getRow(0).getCell(c).setText(h);
                }
                for (int r = 0; r < tRows.size(); r++) {
                    List<String> data = tRows.get(r);
                    for (int c = 0; c < colCount; c++) {
                        String v = c < data.size() && data.get(c) != null ? data.get(c) : "";
                        tableNode.getRow(r + 1).getCell(c).setText(v);
                    }
                }
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    /** Excel 工作表名：去非法字符并限长 31 */
    private String safeSheetName(String title) {
        String s = title == null ? "Sheet" : title.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    /** 上传字节文件到对象存储并返回预签名下载链接 */
    private String uploadBytes(byte[] bytes, String fileName) {
        MultipartFile file = new ByteArrayMultipartFile(bytes, fileName);
        String key = fileStorageService.upload(file, "agent/");
        return fileStorageService.getPreviewUrl(key);
    }

    /** 简易内存 MultipartFile 实现：用于把生成的文件字节流交给存储服务上传 */
    private static final class ByteArrayMultipartFile implements MultipartFile {
        /** 扩展名 → MIME 类型（COS 上传时 Content-Type 不能为空） */
        private static final Map<String, String> MIME_BY_EXT = Map.of(
                ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                ".xls", "application/vnd.ms-excel",
                ".doc", "application/msword"
        );

        private final byte[] bytes;
        private final String name;

        ByteArrayMultipartFile(byte[] bytes, String name) {
            this.bytes = bytes == null ? new byte[0] : bytes;
            this.name = name;
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() {
            int dot = name.lastIndexOf('.');
            String ext = dot >= 0 ? name.substring(dot).toLowerCase() : "";
            String mime = MIME_BY_EXT.get(ext);
            return mime == null ? "application/octet-stream" : mime;
        }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}