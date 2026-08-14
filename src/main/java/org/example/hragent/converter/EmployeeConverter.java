package org.example.hragent.converter;

import org.example.hragent.dto.EmployeeSaveDto;
import org.example.hragent.dto.EmployeeUpdateDto;
import org.example.hragent.entity.Employee;
import org.example.hragent.entity.Employee;
import org.example.hragent.vo.EmployeeVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface EmployeeConverter {

    /**
     * 新增DTO → Entity
     */
    Employee saveDtoToEntity(EmployeeSaveDto dto);

    /**
     * 更新DTO → 覆盖已有Entity
     */
    void updateDtoToEntity(EmployeeUpdateDto dto, @MappingTarget Employee entity);

    /**
     * Entity → VO 返回前端
     */
    EmployeeVO entityToVo(Employee entity);

    /**
     * 集合批量转换：List<Entity> → List<VO>，用于分页
     */
    default List<EmployeeVO> entityListToVoList(List<Employee> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}