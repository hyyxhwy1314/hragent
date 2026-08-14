package org.example.hragent.service;

import org.example.hragent.converter.EmployeeConverter;
import org.example.hragent.dto.EmployeeSaveDto;
import org.example.hragent.entity.Employee;

import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EmployeeService {
    @Autowired
    private EmployeeConverter employeeConverter;
    @Autowired
    private TaskService taskService;
    public Employee save() {
        EmployeeSaveDto employeeSaveDto = new EmployeeSaveDto();
        Task task = taskService.newTask("1");
        taskService.saveTask(task);
        employeeSaveDto.setEmpNo("123456");
        employeeSaveDto.setEmpName("张三");
        Employee employee = employeeConverter.saveDtoToEntity(employeeSaveDto);
        System.out.println(employee.getEmpName());
        return employee;
    }
}
