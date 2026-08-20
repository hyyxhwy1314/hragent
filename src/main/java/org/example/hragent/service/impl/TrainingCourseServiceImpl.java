package org.example.hragent.service.impl;

import org.example.hragent.entity.TrainingCourse;
import org.example.hragent.mapper.TrainingCourseMapper;
import org.example.hragent.service.TrainingCourseService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;

/**
 * 培训课程 Service 实现
 * 读多写少，变更频率低，list + getById 双缓存
 */
@Service
public class TrainingCourseServiceImpl extends BaseServiceImpl<TrainingCourseMapper, TrainingCourse> implements TrainingCourseService {

    @Override
    @Cacheable(value = "training_course", key = "#id", unless = "#result == null")
    public TrainingCourse getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @Cacheable(value = "training_course", key = "'list'")
    public List<TrainingCourse> list() {
        return super.list();
    }

    @Override
    @CacheEvict(value = "training_course", allEntries = true)
    public boolean save(TrainingCourse entity) {
        return super.save(entity);
    }

    @Override
    @CacheEvict(value = "training_course", allEntries = true)
    public boolean updateById(TrainingCourse entity) {
        return super.updateById(entity);
    }

    @Override
    @CacheEvict(value = "training_course", allEntries = true)
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }
}
