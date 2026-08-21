package org.example.hragent.service;

import org.example.hragent.vo.ResumeParsedData;

/**
 * 简历解析服务
 * 从上传的简历文件字节中提取文本并解析为结构化字段
 */
public interface ResumeParserService {

    /**
     * 解析简历，返回结构化字段
     *
     * @param data        文件字节内容（由调用方预读，避免流二次消费）
     * @param fileName    原始文件名（用于类型判断与姓名启发式）
     * @param contentType MIME 类型
     * @return 解析结果，解析失败时字段为空但不抛异常
     */
    ResumeParsedData parse(byte[] data, String fileName, String contentType);
}
