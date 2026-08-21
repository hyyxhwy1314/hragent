package org.example.hragent.converter;

import org.example.hragent.dto.EmployeeSaveDto;
import org.example.hragent.dto.EmployeeUpdateDto;
import org.example.hragent.entity.Employee;
import org.example.hragent.vo.EmployeeVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface EmployeeConverter extends BaseFullConverter<Employee, EmployeeVO, EmployeeSaveDto, EmployeeUpdateDto> {

    @Override
    Employee saveDtoToEntity(EmployeeSaveDto dto);

    @Override
    void updateDtoToEntity(EmployeeUpdateDto dto, @MappingTarget Employee entity);

    @Override
    EmployeeVO entityToVo(Employee entity);
}
