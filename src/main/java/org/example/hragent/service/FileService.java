package org.example.hragent.service;

import org.example.hragent.entity.FileEntity;
import org.example.hragent.vo.FileVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件元信息服务
 * 负责文件元数据持久化与对象存储协调
 */
public interface FileService {

    /**
     * 上传文件并落库记录元信息
     */
    FileVO upload(MultipartFile file, String directory);

    /**
     * 根据文件ID获取文件元信息
     */
    FileEntity getByIdChecked(Long id);

    /**
     * 根据文件ID获取预览URL
     */
    String getPreviewUrl(Long id);

    /**
     * 根据文件ID下载文件内容到输出流，返回已写入字节数
     */
    long download(Long id, java.io.OutputStream out);

    /**
     * 删除文件（对象存储 + 元信息）
     */
    void remove(Long id);

    /**
     * 根据文件ID下载文件内容为字节数组
     */
    byte[] downloadBytes(Long id);
}
