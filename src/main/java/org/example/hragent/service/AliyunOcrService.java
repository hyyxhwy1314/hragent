package org.example.hragent.service;

import java.util.Map;

/**
 * 阿里云 OCR 服务：把图片 / 扫描件 PDF 的原始字节转为可读文本。
 *
 * <p>只负责"识别文本"这一件事，结构化字段抽取交由后续 AI 分析完成。
 * 未配置 AK/SK 时实现类返回 null 并打 WARN，不阻断上传流程。
 */
public interface AliyunOcrService {

    /**
     * 通用文本识别（支持常见图片格式 + 扫描件 PDF 逐页识别）
     *
     * @param data        文件字节
     * @param fileName    文件名（用于类型判断）
     * @param contentType MIME
     * @return 拼接后的全文；识别失败/未配置 AK/SK 返回 null
     */
    String recognizeRawText(byte[] data, String fileName, String contentType);

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
