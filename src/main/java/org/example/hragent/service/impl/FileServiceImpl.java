package org.example.hragent.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.hragent.entity.FileEntity;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.FileMapper;
import org.example.hragent.service.FileService;
import org.example.hragent.service.FileStorageService;
import org.example.hragent.vo.FileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;

/**
 * 文件服务实现
 * 协调对象存储(FileStorageService)与元信息表(t_sys_file)
 */
@Service
public class FileServiceImpl extends ServiceImpl<FileMapper, FileEntity> implements FileService {

    @Autowired
    private FileStorageService fileStorageService;

    @Override
    public FileVO upload(MultipartFile file, String directory) {
        // 1. 上传到对象存储
        String objectKey = fileStorageService.upload(file, directory);
        // 2. 落库元信息
        FileEntity entity = new FileEntity();
        entity.setObjectKey(objectKey);
        entity.setOriginalName(file.getOriginalFilename());
        entity.setFileType(file.getContentType());
        entity.setFileSize(file.getSize());
        entity.setStorageType("COS");
        this.save(entity);
        // 3. 组装返回
        FileVO vo = new FileVO();
        vo.setId(entity.getId());
        vo.setObjectKey(objectKey);
        vo.setOriginalName(file.getOriginalFilename());
        vo.setFileType(file.getContentType());
        vo.setFileSize(file.getSize());
        vo.setStorageType("COS");
        vo.setPreviewUrl(fileStorageService.getPreviewUrl(objectKey));
        return vo;
    }

    @Override
    public FileEntity getByIdChecked(Long id) {
        FileEntity entity = this.getById(id);
        BusinessException.throwIf(entity == null, ErrorCode.DATA_NOT_FOUND);
        return entity;
    }

    @Override
    public String getPreviewUrl(Long id) {
        FileEntity entity = getByIdChecked(id);
        return fileStorageService.getPreviewUrl(entity.getObjectKey());
    }

    @Override
    public long download(Long id, OutputStream out) {
        FileEntity entity = getByIdChecked(id);
        return fileStorageService.download(entity.getObjectKey(), out);
    }

    @Override
    public void remove(Long id) {
        FileEntity entity = getByIdChecked(id);
        // 先删对象存储，再删元信息
        fileStorageService.delete(entity.getObjectKey());
        this.removeById(id);
    }

    @Override
    public byte[] downloadBytes(Long id) {
        FileEntity entity = getByIdChecked(id);
        return fileStorageService.downloadBytes(entity.getObjectKey());
    }
}
