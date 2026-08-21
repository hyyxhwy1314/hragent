package org.example.hragent.converter;

import org.example.hragent.dto.FlowInstanceSaveDto;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.vo.FlowInstanceVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FlowInstanceConverter extends BaseConverter<FlowInstance, FlowInstanceVO> {

    FlowInstance saveDtoToEntity(FlowInstanceSaveDto dto);

    @Override
    FlowInstanceVO entityToVo(FlowInstance entity);
}
