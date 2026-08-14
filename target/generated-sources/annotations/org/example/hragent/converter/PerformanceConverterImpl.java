package org.example.hragent.converter;

import javax.annotation.processing.Generated;
import org.example.hragent.dto.PerformanceSaveDto;
import org.example.hragent.dto.PerformanceUpdateDto;
import org.example.hragent.entity.TPerformance;
import org.example.hragent.vo.PerformanceVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-14T10:43:47+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class PerformanceConverterImpl implements PerformanceConverter {

    @Override
    public TPerformance saveDtoToEntity(PerformanceSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        TPerformance tPerformance = new TPerformance();

        tPerformance.setEmpId( dto.getEmpId() );
        tPerformance.setPeriodCode( dto.getPeriodCode() );
        tPerformance.setKpiJson( dto.getKpiJson() );
        tPerformance.setSelfScore( dto.getSelfScore() );
        tPerformance.setLeaderScore( dto.getLeaderScore() );
        tPerformance.setFinalScore( dto.getFinalScore() );
        tPerformance.setPerformanceLevel( dto.getPerformanceLevel() );
        tPerformance.setAiComment( dto.getAiComment() );
        tPerformance.setFlowInstanceId( dto.getFlowInstanceId() );
        tPerformance.setStatus( dto.getStatus() );

        return tPerformance;
    }

    @Override
    public void updateDtoToEntity(PerformanceUpdateDto dto, TPerformance entity) {
        if ( dto == null ) {
            return;
        }

        entity.setId( dto.getId() );
        entity.setEmpId( dto.getEmpId() );
        entity.setPeriodCode( dto.getPeriodCode() );
        entity.setKpiJson( dto.getKpiJson() );
        entity.setSelfScore( dto.getSelfScore() );
        entity.setLeaderScore( dto.getLeaderScore() );
        entity.setFinalScore( dto.getFinalScore() );
        entity.setPerformanceLevel( dto.getPerformanceLevel() );
        entity.setAiComment( dto.getAiComment() );
        entity.setFlowInstanceId( dto.getFlowInstanceId() );
        entity.setStatus( dto.getStatus() );
    }

    @Override
    public PerformanceVO entityToVo(TPerformance entity) {
        if ( entity == null ) {
            return null;
        }

        PerformanceVO performanceVO = new PerformanceVO();

        performanceVO.setId( entity.getId() );
        performanceVO.setEmpId( entity.getEmpId() );
        performanceVO.setPeriodCode( entity.getPeriodCode() );
        performanceVO.setKpiJson( entity.getKpiJson() );
        performanceVO.setSelfScore( entity.getSelfScore() );
        performanceVO.setLeaderScore( entity.getLeaderScore() );
        performanceVO.setFinalScore( entity.getFinalScore() );
        performanceVO.setPerformanceLevel( entity.getPerformanceLevel() );
        performanceVO.setAiComment( entity.getAiComment() );
        performanceVO.setFlowInstanceId( entity.getFlowInstanceId() );
        performanceVO.setStatus( entity.getStatus() );

        return performanceVO;
    }
}
