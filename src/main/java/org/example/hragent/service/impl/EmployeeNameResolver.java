package org.example.hragent.service.impl;

import org.example.hragent.entity.Employee;
import org.example.hragent.mapper.EmployeeMapper;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 员工姓名批量解析：ID 列表 → (ID, empName) Map
 * <p>
 * 统一收敛「先批量查、再映射」的姓名回填逻辑，避免各服务重复实现并造成逐条查询（N+1）。
 */
@Component
public class EmployeeNameResolver {

    private final EmployeeMapper employeeMapper;

    public EmployeeNameResolver(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    /** 批量解析员工姓名；空集合返回空 Map，避免无谓查询 */
    public Map<Long, String> resolveNames(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return new HashMap<>();
        }
        return employeeMapper.selectBatchIds(distinctIds).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getEmpName, (a, b) -> a));
    }
}
