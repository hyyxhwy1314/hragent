package org.example.hragent.converter;

import javax.annotation.processing.Generated;
import org.example.hragent.dto.PerformanceSaveDto;
import org.example.hragent.dto.PerformanceUpdateDto;
import org.example.hragent.entity.Performance;
import org.example.hragent.vo.PerformanceVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-15T17:23:12+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class PerformanceConverterImpl implements PerformanceConverter {

    @Override
    public Performance saveDtoToEntity(PerformanceSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        Performance performance = new Performance();

        performance.setEmpId( dto.getEmpId() );
        performance.setPeriodCode( dto.getPeriodCode() );
        performance.setKpiJson( dto.getKpiJson() );
        performance.setSelfScore( dto.getSelfScore() );
        performance.setLeaderScore( dto.getLeaderScore() );
        performance.setFinalScore( dto.getFinalScore() );
        performance.setPerformanceLevel( dto.getPerformanceLevel() );
        performance.setAiComment( dto.getAiComment() );
        performance.setFlowInstanceId( dto.getFlowInstanceId() );
        performance.setStatus( dto.getStatus() );

        return performance;
    }

    @Override
    public void updateDtoToEntity(PerformanceUpdateDto dto, Performance entity) {
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
    public PerformanceVO entityToVo(Performance entity) {
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
