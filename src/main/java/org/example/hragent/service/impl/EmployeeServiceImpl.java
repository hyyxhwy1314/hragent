package org.example.hragent.service.impl;

import org.example.hragent.entity.Employee;
import org.example.hragent.mapper.EmployeeMapper;
import org.example.hragent.service.EmployeeService;
import org.springframework.stereotype.Service;

@Service
public class EmployeeServiceImpl extends BaseServiceImpl<EmployeeMapper, Employee> implements EmployeeService {
}
