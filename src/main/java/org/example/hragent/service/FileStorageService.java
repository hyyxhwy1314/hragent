package org.example.hragent.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;

/**
 * 文件存储服务抽象接口
 * 屏蔽底层对象存储实现细节，便于后续切换不同存储厂商
 */
public interface FileStorageService {

    /**
     * 上传文件
     *
     * @param file      上传文件
     * @param directory 存储目录前缀（如 resume/），可为空
     * @return 存储对象 key
     */
    String upload(MultipartFile file, String directory);

    /**
     * 下载文件到输出流
     *
     * @param objectKey 存储对象 key
     * @param out       输出流
     * @return 文件内容字节数，-1 表示对象不存在
     */
    long download(String objectKey, OutputStream out);

    /**
     * 生成预签名预览URL
     *
     * @param objectKey 存储对象 key
     * @return 预签名URL
     */
    String getPreviewUrl(String objectKey);

    /**
     * 删除文件
     *
     * @param objectKey 存储对象 key
     */
    void delete(String objectKey);
}
