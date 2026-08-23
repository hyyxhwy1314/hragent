package org.example.hragent.service.impl;

import org.example.hragent.entity.Employee;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.service.EmployeeService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.Serializable;

/**
 * 员工 Service 实现
 * <p>
 * 缓存策略：getById 缓存详情；save/updateById/removeById 清全部缓存。
 * 密码：新增员工若未指定密码，设默认密码 123456 的 BCrypt 哈希。
 */
@Service
public class EmployeeServiceImpl extends BaseServiceImpl<EmployeeMapper, Employee> implements EmployeeService {

    private final BCryptPasswordEncoder passwordEncoder;

    public EmployeeServiceImpl(BCryptPasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Cacheable(value = "employee", key = "#id", unless = "#result == null")
    public Employee getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = "employee", allEntries = true)
    public boolean save(Employee entity) {
        // 新增员工：密码为空则设默认密码 123456 的哈希
        if (entity.getPassword() == null || entity.getPassword().isBlank()) {
            entity.setPassword(passwordEncoder.encode("123456"));
        } else {
            // 显式传入明文密码时哈希后存储
            entity.setPassword(passwordEncoder.encode(entity.getPassword()));
        }
        // 角色为空默认 EMPLOYEE
        if (entity.getRole() == null || entity.getRole().isBlank()) {
            entity.setRole("EMPLOYEE");
        }
        return super.save(entity);
    }

    @Override
    @CacheEvict(value = "employee", allEntries = true)
    public boolean updateById(Employee entity) {
        // 更新时不清空密码：若未传 password 则保持原密码（置空避免被覆盖为 null）
        entity.setPassword(null);
        return super.updateById(entity);
    }

    @Override
    @CacheEvict(value = "employee", allEntries = true)
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }
}
