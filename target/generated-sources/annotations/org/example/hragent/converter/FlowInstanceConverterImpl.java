package org.example.hragent.converter;

import javax.annotation.processing.Generated;
import org.example.hragent.dto.FlowInstanceSaveDto;
import org.example.hragent.entity.FlowInstance;
import org.example.hragent.vo.FlowInstanceVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-16T14:12:53+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.8 (Oracle Corporation)"
)
@Component
public class FlowInstanceConverterImpl implements FlowInstanceConverter {

    @Override
    public FlowInstance saveDtoToEntity(FlowInstanceSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        FlowInstance flowInstance = new FlowInstance();

        flowInstance.setFlowType( dto.getFlowType() );
        flowInstance.setBizId( dto.getBizId() );
        flowInstance.setApplyEmpId( dto.getApplyEmpId() );
        flowInstance.setFlowableProcInstId( dto.getFlowableProcInstId() );
        flowInstance.setBizJson( dto.getBizJson() );

        return flowInstance;
    }

    @Override
    public FlowInstanceVO entityToVo(FlowInstance entity) {
        if ( entity == null ) {
            return null;
        }

        FlowInstanceVO flowInstanceVO = new FlowInstanceVO();

        flowInstanceVO.setId( entity.getId() );
        flowInstanceVO.setFlowType( entity.getFlowType() );
        flowInstanceVO.setBizId( entity.getBizId() );
        flowInstanceVO.setApplyEmpId( entity.getApplyEmpId() );
        flowInstanceVO.setFlowableProcInstId( entity.getFlowableProcInstId() );
        flowInstanceVO.setBizJson( entity.getBizJson() );

        return flowInstanceVO;
    }
}
