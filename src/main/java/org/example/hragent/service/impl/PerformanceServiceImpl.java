package org.example.hragent.service.impl;

import org.example.hragent.entity.Performance;
import org.example.hragent.mapper.PerformanceMapper;
import org.example.hragent.service.PerformanceService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;

/**
 * 绩效 Service 实现
 * 季度数据稳定（创建后基本不变），仅 getById 缓存
 */
@Service
public class PerformanceServiceImpl extends BaseServiceImpl<PerformanceMapper, Performance> implements PerformanceService {

    @Override
    @Cacheable(value = "performance", key = "#id", unless = "#result == null")
    public Performance getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = "performance", allEntries = true)
    public boolean save(Performance entity) {
        return super.save(entity);
    }

    @Override
    @CacheEvict(value = "performance", allEntries = true)
    public boolean updateById(Performance entity) {
        return super.updateById(entity);
    }

    @Override
    @CacheEvict(value = "performance", allEntries = true)
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }
}
