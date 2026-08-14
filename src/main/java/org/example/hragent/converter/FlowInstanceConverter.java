package org.example.hragent.converter;

import org.example.hragent.dto.FlowInstanceSaveDto;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.vo.FlowInstanceVO;
import org.mapstruct.Mapper;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface FlowInstanceConverter {

    FlowInstance saveDtoToEntity(FlowInstanceSaveDto dto);

    FlowInstanceVO entityToVo(FlowInstance entity);

    default List<FlowInstanceVO> entityListToVoList(List<FlowInstance> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}