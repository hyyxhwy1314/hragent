package org.example.hragent.service;

import org.example.hragent.vo.ResumeParsedData;

import java.util.Map;

/**
 * 阿里云 OCR 服务：负责把图片/PDF扫描件的原始字节转为"可解析文本"或"结构化简历字段"。
 *
 * 设计上拆两层：
 *   1) recognizeRawText(...) —— 返回 OCR 原始识别文本（通用印刷体 RecognizeGeneral）。
 *      当调用方已经有 PDF 文本但检测到是扫描件时，会再 fallback 走这里拿 OCR 文本，
 *      然后继续复用原有的正则解析链路（与纯文本版简历字段对齐，保证输出格式统一）。
 *   2) recognizeResumeStructured(...) —— 调用阿里云简历识别 RecognizeResume，
 *      让平台帮忙抽取姓名/学校/专业/邮箱/手机号/期望城市/期望岗位/学历等，返回统一的
 *      ResumeParsedData，解析准确率一般高于本地正则（≥85% 验收标准的主要依赖）。
 *
 * 未配置 AK/SK 时，实现类必须保持"空值返回 + 仅日志"，不能抛异常阻断上传流程。
 */
public interface AliyunOcrService {

    /**
     * 通用文本识别（支持常见图片格式 + 扫描件 PDF）
     *
     * @param data        文件字节
     * @param fileName    文件名（用于类型判断）
     * @param contentType MIME
     * @return 拼接后的全文；识别失败/未配置 AK/SK 返回 null
     */
    String recognizeRawText(byte[] data, String fileName, String contentType);

    /**
     * 简历结构化识别：直接返回对齐了 ResumeParsedData 的字段
     *
     * @param data        文件字节
     * @param fileName    文件名（用于类型判断）
     * @param contentType MIME
     * @return 结构化结果（未配置 OCR 或识别失败返回 null，调用方会回退 PDFBox+正则）
     */
    ResumeParsedData recognizeResumeStructured(byte[] data, String fileName, String contentType);

    /**
     * 调试用：直接调用 RecognizeAllText 并返回阿里云原始响应的完整诊断信息
     * （code/message/requestId/content/contentLength/subCode 等），用于定位识别失败原因。
     * 仅供调试接口使用，业务链路不要调用。
     *
     * @param fileBytes 图片字节
     * @return 诊断信息 Map
     */
    Map<String, Object> recognizeAllTextDebug(byte[] fileBytes);
}
