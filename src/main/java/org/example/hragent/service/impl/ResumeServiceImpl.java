package org.example.hragent.service.impl;

import org.example.hragent.entity.Resume;
import org.example.hragent.mapper.ResumeMapper;
import org.example.hragent.service.ResumeService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;

/**
 * 简历 Service 实现
 * 简历频繁投递更新，列表/详情变化都快
 * 仅 getById 缓存 + 写操作主动失效
 */
@Service
public class ResumeServiceImpl extends BaseServiceImpl<ResumeMapper, Resume> implements ResumeService {

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
}
