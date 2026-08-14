package org.example.hragent.converter;

import org.example.hragent.dto.FlowInstanceSaveDto;
import org.example.hragent.entity.TFlowInstance;
import org.example.hragent.vo.FlowInstanceVO;
import org.mapstruct.Mapper;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface FlowInstanceConverter {

    TFlowInstance saveDtoToEntity(FlowInstanceSaveDto dto);

    FlowInstanceVO entityToVo(TFlowInstance entity);

    default List<FlowInstanceVO> entityListToVoList(List<TFlowInstance> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}