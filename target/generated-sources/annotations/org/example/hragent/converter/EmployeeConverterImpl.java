package org.example.hragent.converter;

import java.time.format.DateTimeFormatter;
import javax.annotation.processing.Generated;
import org.example.hragent.dto.EmployeeSaveDto;
import org.example.hragent.dto.EmployeeUpdateDto;
import org.example.hragent.entity.Employee;
import org.example.hragent.vo.EmployeeVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-19T16:58:49+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class EmployeeConverterImpl implements EmployeeConverter {

    @Override
    public Employee saveDtoToEntity(EmployeeSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        Employee employee = new Employee();

        employee.setEmpNo( dto.getEmpNo() );
        employee.setEmpName( dto.getEmpName() );
        employee.setGender( dto.getGender() );
        employee.setBirthDate( dto.getBirthDate() );
        employee.setPhone( dto.getPhone() );
        employee.setEmail( dto.getEmail() );
        employee.setIdCard( dto.getIdCard() );
        employee.setDeptName( dto.getDeptName() );
        employee.setPositionName( dto.getPositionName() );
        employee.setEntryDate( dto.getEntryDate() );
        employee.setRegularDate( dto.getRegularDate() );
        employee.setLeaveDate( dto.getLeaveDate() );
        employee.setEmpStatus( dto.getEmpStatus() );
        employee.setBaseSalary( dto.getBaseSalary() );
        employee.setWorkCity( dto.getWorkCity() );
        employee.setRemark( dto.getRemark() );

        return employee;
    }

    @Override
    public void updateDtoToEntity(EmployeeUpdateDto dto, Employee entity) {
        if ( dto == null ) {
            return;
        }

        entity.setId( dto.getId() );
        entity.setEmpNo( dto.getEmpNo() );
        entity.setEmpName( dto.getEmpName() );
        entity.setGender( dto.getGender() );
        entity.setBirthDate( dto.getBirthDate() );
        entity.setPhone( dto.getPhone() );
        entity.setEmail( dto.getEmail() );
        entity.setIdCard( dto.getIdCard() );
        entity.setDeptName( dto.getDeptName() );
        entity.setPositionName( dto.getPositionName() );
        entity.setEntryDate( dto.getEntryDate() );
        entity.setRegularDate( dto.getRegularDate() );
        entity.setLeaveDate( dto.getLeaveDate() );
        entity.setEmpStatus( dto.getEmpStatus() );
        entity.setBaseSalary( dto.getBaseSalary() );
        entity.setWorkCity( dto.getWorkCity() );
        entity.setRemark( dto.getRemark() );
    }

    @Override
    public EmployeeVO entityToVo(Employee entity) {
        if ( entity == null ) {
            return null;
        }

        EmployeeVO employeeVO = new EmployeeVO();

        employeeVO.setId( entity.getId() );
        employeeVO.setEmpNo( entity.getEmpNo() );
        employeeVO.setEmpName( entity.getEmpName() );
        employeeVO.setGender( entity.getGender() );
        employeeVO.setBirthDate( entity.getBirthDate() );
        employeeVO.setPhone( entity.getPhone() );
        employeeVO.setEmail( entity.getEmail() );
        employeeVO.setIdCard( entity.getIdCard() );
        employeeVO.setDeptName( entity.getDeptName() );
        employeeVO.setPositionName( entity.getPositionName() );
        employeeVO.setEntryDate( entity.getEntryDate() );
        employeeVO.setRegularDate( entity.getRegularDate() );
        employeeVO.setLeaveDate( entity.getLeaveDate() );
        employeeVO.setEmpStatus( entity.getEmpStatus() );
        employeeVO.setBaseSalary( entity.getBaseSalary() );
        employeeVO.setWorkCity( entity.getWorkCity() );
        employeeVO.setRemark( entity.getRemark() );
        if ( entity.getCreateTime() != null ) {
            employeeVO.setCreateTime( DateTimeFormatter.ISO_LOCAL_DATE_TIME.format( entity.getCreateTime() ) );
        }
        if ( entity.getUpdateTime() != null ) {
            employeeVO.setUpdateTime( DateTimeFormatter.ISO_LOCAL_DATE_TIME.format( entity.getUpdateTime() ) );
        }

        return employeeVO;
    }
}
