package org.example.hragent.service.impl;

import org.example.hragent.entity.JobPost;
import org.example.hragent.mapper.JobPostMapper;
import org.example.hragent.service.JobPostService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;

/**
 * 岗位 Service 实现
 * 招聘门户频繁查询岗位详情，仅 getById 缓存
 * 列表因筛选条件多变不缓存
 */
@Service
public class JobPostServiceImpl extends BaseServiceImpl<JobPostMapper, JobPost> implements JobPostService {

    @Override
    @Cacheable(value = "job_post", key = "#id", unless = "#result == null")
    public JobPost getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = "job_post", allEntries = true)
    public boolean save(JobPost entity) {
        return super.save(entity);
    }

    @Override
    @CacheEvict(value = "job_post", allEntries = true)
    public boolean updateById(JobPost entity) {
        return super.updateById(entity);
    }

    @Override
    @CacheEvict(value = "job_post", allEntries = true)
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }
}
