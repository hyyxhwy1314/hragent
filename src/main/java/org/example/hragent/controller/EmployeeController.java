package org.example.hragent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.converter.EmployeeConverter;
import org.example.hragent.dto.EmployeeQueryDto;
import org.example.hragent.dto.EmployeeSaveDto;
import org.example.hragent.dto.EmployeeUpdateDto;
import org.example.hragent.entity.Employee;
import org.example.hragent.service.EmployeeService;
import org.example.hragent.vo.EmployeeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/employees")
public class EmployeeController extends BaseCrudController<Employee, EmployeeVO, EmployeeQueryDto, EmployeeSaveDto, EmployeeUpdateDto> {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeConverter employeeConverter;

    @Override
    protected EmployeeService baseService() {
        return employeeService;
    }

    @Override
    protected EmployeeConverter baseConverter() {
        return employeeConverter;
    }

    @Override
    protected EmployeeConverter fullConverter() {
        return employeeConverter;
    }

    @Override
    protected LambdaQueryWrapper<Employee> buildWrapper(EmployeeQueryDto queryDto) {
        LambdaQueryWrapper<Employee> w = new LambdaQueryWrapper<>();
        w.like(queryDto.getEmpNo() != null && !queryDto.getEmpNo().isBlank(), Employee::getEmpNo, queryDto.getEmpNo())
         .like(queryDto.getEmpName() != null && !queryDto.getEmpName().isBlank(), Employee::getEmpName, queryDto.getEmpName())
         .like(queryDto.getDeptName() != null && !queryDto.getDeptName().isBlank(), Employee::getDeptName, queryDto.getDeptName())
         .eq(queryDto.getEmpStatus() != null, Employee::getEmpStatus, queryDto.getEmpStatus());
        return w;
    }
}
