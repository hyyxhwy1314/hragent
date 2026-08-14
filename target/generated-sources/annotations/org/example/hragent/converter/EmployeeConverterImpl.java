package org.example.hragent.converter;

import javax.annotation.processing.Generated;
import org.example.hragent.dto.EmployeeSaveDto;
import org.example.hragent.dto.EmployeeUpdateDto;
import org.example.hragent.entity.TEmployee;
import org.example.hragent.vo.EmployeeVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-14T10:43:47+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class EmployeeConverterImpl implements EmployeeConverter {

    @Override
    public TEmployee saveDtoToEntity(EmployeeSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        TEmployee tEmployee = new TEmployee();

        tEmployee.setEmpNo( dto.getEmpNo() );
        tEmployee.setEmpName( dto.getEmpName() );
        tEmployee.setGender( dto.getGender() );
        tEmployee.setBirthDate( dto.getBirthDate() );
        tEmployee.setPhone( dto.getPhone() );
        tEmployee.setEmail( dto.getEmail() );
        tEmployee.setIdCard( dto.getIdCard() );
        tEmployee.setDeptName( dto.getDeptName() );
        tEmployee.setPositionName( dto.getPositionName() );
        tEmployee.setEntryDate( dto.getEntryDate() );
        tEmployee.setRegularDate( dto.getRegularDate() );
        tEmployee.setLeaveDate( dto.getLeaveDate() );
        tEmployee.setEmpStatus( dto.getEmpStatus() );
        tEmployee.setBaseSalary( dto.getBaseSalary() );
        tEmployee.setWorkCity( dto.getWorkCity() );
        tEmployee.setRemark( dto.getRemark() );

        return tEmployee;
    }

    @Override
    public void updateDtoToEntity(EmployeeUpdateDto dto, TEmployee entity) {
        if ( dto == null ) {
            return;
        }

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
    public EmployeeVO entityToVo(TEmployee entity) {
        if ( entity == null ) {
            return null;
        }

        EmployeeVO employeeVO = new EmployeeVO();

        employeeVO.setEmpNo( entity.getEmpNo() );
        employeeVO.setEmpName( entity.getEmpName() );
        employeeVO.setGender( entity.getGender() );
        employeeVO.setBirthDate( entity.getBirthDate() );
        employeeVO.setPhone( entity.getPhone() );
        employeeVO.setEmail( entity.getEmail() );
        employeeVO.setDeptName( entity.getDeptName() );
        employeeVO.setPositionName( entity.getPositionName() );
        employeeVO.setEntryDate( entity.getEntryDate() );
        employeeVO.setRegularDate( entity.getRegularDate() );
        employeeVO.setLeaveDate( entity.getLeaveDate() );
        employeeVO.setEmpStatus( entity.getEmpStatus() );
        employeeVO.setWorkCity( entity.getWorkCity() );
        employeeVO.setRemark( entity.getRemark() );

        return employeeVO;
    }
}
