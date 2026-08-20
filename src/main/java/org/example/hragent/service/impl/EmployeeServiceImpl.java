package org.example.hragent.service.impl;

import org.example.hragent.entity.Employee;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.service.EmployeeService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;

/**
 * 员工 Service 实现
 * 详情查询频繁，仅 getById 缓存
 * 列表因筛选条件多变不缓存
 */
@Service
public class EmployeeServiceImpl extends BaseServiceImpl<EmployeeMapper, Employee> implements EmployeeService {

    @Override
    @Cacheable(value = "employee", key = "#id", unless = "#result == null")
    public Employee getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = "employee", allEntries = true)
    public boolean save(Employee entity) {
        return super.save(entity);
    }

    @Override
    @CacheEvict(value = "employee", allEntries = true)
    public boolean updateById(Employee entity) {
        return super.updateById(entity);
    }

    @Override
    @CacheEvict(value = "employee", allEntries = true)
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }
}
