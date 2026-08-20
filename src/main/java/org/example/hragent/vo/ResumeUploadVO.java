package org.example.hragent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简历上传结果
 * 同时返回文件元信息与解析出的结构化字段，供前端回填
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeUploadVO {

    /** 文件记录ID，对应 t_resume.resume_file_id */
    private Long fileId;

    /** 对象存储 key */
    private String objectKey;

    /** 原始文件名 */
    private String originalName;

    /** 预签名预览URL */
    private String previewUrl;

    /** 解析出的简历字段 */
    private ResumeParsedData parsed;
}
