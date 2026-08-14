package org.example.hragent.converter;

import org.example.hragent.dto.PerformanceSaveDto;
import org.example.hragent.dto.PerformanceUpdateDto;
import org.example.hragent.entity.Performance;
import org.example.hragent.entity.Performance;
import org.example.hragent.vo.PerformanceVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PerformanceConverter {

    Performance saveDtoToEntity(PerformanceSaveDto dto);

    void updateDtoToEntity(PerformanceUpdateDto dto, @MappingTarget Performance entity);

    PerformanceVO entityToVo(Performance entity);

    default List<PerformanceVO> entityListToVoList(List<Performance> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}