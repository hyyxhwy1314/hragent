package org.example.hragent.converter;

import org.example.hragent.dto.PerformanceSaveDto;
import org.example.hragent.dto.PerformanceUpdateDto;
import org.example.hragent.entity.TPerformance;
import org.example.hragent.vo.PerformanceVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PerformanceConverter {

    TPerformance saveDtoToEntity(PerformanceSaveDto dto);

    void updateDtoToEntity(PerformanceUpdateDto dto, @MappingTarget TPerformance entity);

    PerformanceVO entityToVo(TPerformance entity);

    default List<PerformanceVO> entityListToVoList(List<TPerformance> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}