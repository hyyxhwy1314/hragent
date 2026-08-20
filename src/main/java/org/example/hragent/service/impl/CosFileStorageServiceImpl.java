package org.example.hragent.service.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.example.hragent.config.FileStorageProperties;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.Date;
import java.util.UUID;

/**
 * 基于 COS 的文件存储服务实现
 */
@Slf4j
@Service
public class CosFileStorageServiceImpl implements FileStorageService {

    @Autowired
    private COSClient cosClient;

    @Autowired
    private FileStorageProperties props;

    @Override
    public String upload(MultipartFile file, String directory) {
        BusinessException.throwIf(file == null || file.isEmpty(), ErrorCode.PARAM_ERROR, "上传文件不能为空");
        String dir = (directory == null || directory.isBlank()) ? props.getPrefix() : directory;
        String originalName = file.getOriginalFilename();
        String suffix = "";
        if (originalName != null && originalName.contains(".")) {
            suffix = originalName.substring(originalName.lastIndexOf("."));
        }
        // 防止文件名冲突：UUID + 后缀
        String objectKey = dir + UUID.randomUUID().toString().replace("-", "") + suffix;
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            PutObjectRequest request = new PutObjectRequest(
                    props.getBucket(), objectKey, file.getInputStream(), metadata);
            cosClient.putObject(request);
            log.info("文件上传成功 bucket={}, key={}, size={}", props.getBucket(), objectKey, file.getSize());
            return objectKey;
        } catch (CosServiceException e) {
            log.error("文件上传COS失败 key={}, msg={}", objectKey, e.getErrorMessage());
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "文件上传失败：" + e.getErrorMessage());
        } catch (Exception e) {
            log.error("文件上传异常 key={}", objectKey, e);
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "文件上传失败");
        }
    }

    @Override
    public long download(String objectKey, OutputStream out) {
        try {
            GetObjectRequest getRequest = new GetObjectRequest(props.getBucket(), objectKey);
            COSObject cosObject = cosClient.getObject(getRequest);
            if (cosObject == null) {
                return -1L;
            }
            ObjectMetadata metadata = cosObject.getObjectMetadata();
            try (COSObjectInputStream in = cosObject.getObjectContent()) {
                return IOUtils.copy(in, out);
            }
        } catch (CosServiceException e) {
            log.warn("文件下载失败 key={}, msg={}", objectKey, e.getErrorMessage());
            return -1L;
        } catch (Exception e) {
            log.error("文件下载异常 key={}", objectKey, e);
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "文件下载失败");
        }
    }

    @Override
    public String getPreviewUrl(String objectKey) {
        try {
            Date expiration = new Date(System.currentTimeMillis() + props.getExpire() * 1000L);
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(props.getBucket(), objectKey, HttpMethodName.GET);
            request.setExpiration(expiration);
            return cosClient.generatePresignedUrl(request).toString();
        } catch (Exception e) {
            log.error("生成预签名URL异常 key={}", objectKey, e);
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "生成预览链接失败");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            cosClient.deleteObject(props.getBucket(), objectKey);
            log.info("文件删除成功 key={}", objectKey);
        } catch (Exception e) {
            log.error("文件删除异常 key={}", objectKey, e);
        }
    }
}
