package org.example.hragent.service.impl;

import org.example.hragent.entity.FileEntity;
import org.example.hragent.entity.Resume;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.mapper.ResumeMapper;
import org.example.hragent.service.FileService;
import org.example.hragent.service.ResumeParserService;
import org.example.hragent.service.ResumeService;
import org.example.hragent.vo.FileVO;
import org.example.hragent.vo.ResumeParsedData;
import org.example.hragent.vo.ResumeUploadVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.io.Serializable;

import lombok.extern.slf4j.Slf4j;

/**
 * 简历 Service 实现
 * 简历频繁投递更新，列表/详情变化都快
 * 仅 getById 缓存 + 写操作主动失效
 */
@Slf4j
@Service
public class ResumeServiceImpl extends BaseServiceImpl<ResumeMapper, Resume> implements ResumeService {

    @Autowired
    private FileService fileService;

    @Autowired
    private ResumeParserService resumeParserService;

    @Override
    @Cacheable(value = "resume", key = "#id", unless = "#result == null")
    public Resume getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = "resume", allEntries = true)
    public boolean save(Resume entity) {
        return super.save(entity);
    }

    @Override
    @CacheEvict(value = "resume", allEntries = true)
    public boolean updateById(Resume entity) {
        return super.updateById(entity);
    }

    @Override
    @CacheEvict(value = "resume", allEntries = true)
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }

    @Override
    public ResumeUploadVO uploadResumeFile(MultipartFile file) {
        // 1. 先预读文件字节，供解析器使用，避免 COS 上传消费流后二次读取失败
        byte[] data = new byte[0];
        try {
            data = file.getBytes();
        } catch (Exception e) {
            log.warn("预读上传文件字节失败 file={}", file.getOriginalFilename(), e);
        }
        // 2. 上传文件并落库元信息
        FileVO fileVO = fileService.upload(file, "resume/");
        // 3. 解析简历字段（失败不影响上传）
        ResumeParsedData parsed = new ResumeParsedData();
        try {
            parsed = resumeParserService.parse(data, file.getOriginalFilename(), file.getContentType());
        } catch (Exception e) {
            log.warn("简历解析异常 file={}", file.getOriginalFilename(), e);
        }
        // 4. 组装返回
        ResumeUploadVO vo = new ResumeUploadVO();
        vo.setFileId(fileVO.getId());
        vo.setObjectKey(fileVO.getObjectKey());
        vo.setOriginalName(fileVO.getOriginalName());
        vo.setPreviewUrl(fileVO.getPreviewUrl());
        vo.setParsed(parsed);
        return vo;
    }

    @Override
    @CacheEvict(value = "resume", allEntries = true)
    public boolean archive(Long id) {
        Resume resume = this.getByIdChecked(id);
        resume.setResumeStatus(STATUS_ARCHIVED);
        return this.updateById(resume);
    }

    @Override
    public String getFilePreviewUrl(Long resumeId) {
        Resume resume = this.getByIdChecked(resumeId);
        BusinessException.throwIf(resume.getResumeFileId() == null, ErrorCode.PARAM_ERROR, "该简历未上传附件");
        return fileService.getPreviewUrl(resume.getResumeFileId());
    }

    @Override
    public String downloadResumeFile(Long resumeId, OutputStream out) {
        Resume resume = this.getByIdChecked(resumeId);
        BusinessException.throwIf(resume.getResumeFileId() == null, ErrorCode.PARAM_ERROR, "该简历未上传附件");
        FileEntity fileEntity = fileService.getByIdChecked(resume.getResumeFileId());
        long size = fileService.download(resume.getResumeFileId(), out);
        BusinessException.throwIf(size < 0, ErrorCode.OPERATION_FAILED, "文件下载失败");
        return fileEntity.getOriginalName();
    }
}
