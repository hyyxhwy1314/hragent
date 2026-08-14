package org.example.hragent.controller;

import org.example.hragent.converter.EmployeeConverter;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.Employee;
import org.example.hragent.service.EmployeeService;
import org.example.hragent.vo.EmployeeVO;
import org.example.hragent.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class test {
    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private EmployeeConverter employeeConverter;
    @GetMapping("/test")
    public R<EmployeeVO> test() {
        Employee Employee = employeeService.save();
        EmployeeVO employeeVO = employeeConverter.entityToVo(Employee);
        return R.ok(employeeVO);
    }
}
