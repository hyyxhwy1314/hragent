package org.example.hragent.converter;

import org.example.hragent.dto.PerformanceSaveDto;
import org.example.hragent.dto.PerformanceUpdateDto;
import org.example.hragent.entity.Performance;
import org.example.hragent.vo.PerformanceVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PerformanceConverter extends BaseFullConverter<Performance, PerformanceVO, PerformanceSaveDto, PerformanceUpdateDto> {

    @Override
    Performance saveDtoToEntity(PerformanceSaveDto dto);

    @Override
    void updateDtoToEntity(PerformanceUpdateDto dto, @MappingTarget Performance entity);

    @Override
    PerformanceVO entityToVo(Performance entity);
}
