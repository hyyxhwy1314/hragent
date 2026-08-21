package org.example.hragent.service;

import org.example.hragent.entity.Resume;
import org.example.hragent.vo.ResumeUploadVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;

public interface ResumeService extends BaseService<Resume> {

    /** 简历归档状态码 */
    int STATUS_ARCHIVED = 4;

    /**
     * 上传简历附件并解析字段，返回文件元信息与解析结果
     */
    ResumeUploadVO uploadResumeFile(MultipartFile file);

    /**
     * 归档简历
     */
    boolean archive(Long id);

    /**
     * 获取简历附件预览URL
     */
    String getFilePreviewUrl(Long resumeId);

    /**
     * 下载简历附件到输出流，返回文件名
     */
    String downloadResumeFile(Long resumeId, OutputStream out);
}
