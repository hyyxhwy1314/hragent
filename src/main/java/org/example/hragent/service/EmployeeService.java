package org.example.hragent.service;

import org.example.hragent.converter.EmployeeConverter;
import org.example.hragent.dto.EmployeeSaveDto;
import org.example.hragent.entity.TEmployee;
import org.example.hragent.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EmployeeService {
    @Autowired
    private EmployeeConverter employeeConverter;
    public TEmployee save() {
        EmployeeSaveDto employeeSaveDto = new EmployeeSaveDto();
        employeeSaveDto.setEmpNo("123456");
        employeeSaveDto.setEmpName("张三");
        TEmployee tEmployee = employeeConverter.saveDtoToEntity(employeeSaveDto);
        System.out.println(tEmployee.getEmpName());
        return tEmployee;
    }
}
