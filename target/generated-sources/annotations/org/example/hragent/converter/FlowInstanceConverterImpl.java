package org.example.hragent.converter;

import javax.annotation.processing.Generated;
import org.example.hragent.dto.FlowInstanceSaveDto;
import org.example.hragent.entity.TFlowInstance;
import org.example.hragent.vo.FlowInstanceVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-14T10:43:46+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class FlowInstanceConverterImpl implements FlowInstanceConverter {

    @Override
    public TFlowInstance saveDtoToEntity(FlowInstanceSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        TFlowInstance tFlowInstance = new TFlowInstance();

        tFlowInstance.setFlowType( dto.getFlowType() );
        tFlowInstance.setBizId( dto.getBizId() );
        tFlowInstance.setApplyEmpId( dto.getApplyEmpId() );
        tFlowInstance.setFlowableProcInstId( dto.getFlowableProcInstId() );
        tFlowInstance.setBizJson( dto.getBizJson() );

        return tFlowInstance;
    }

    @Override
    public FlowInstanceVO entityToVo(TFlowInstance entity) {
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
