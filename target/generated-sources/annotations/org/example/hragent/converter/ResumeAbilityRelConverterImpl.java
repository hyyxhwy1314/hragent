package org.example.hragent.converter;

import javax.annotation.processing.Generated;
import org.example.hragent.dto.ResumeAbilityRelSaveDto;
import org.example.hragent.entity.TResumeAbilityRel;
import org.example.hragent.vo.ResumeAbilityRelVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-14T10:43:46+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class ResumeAbilityRelConverterImpl implements ResumeAbilityRelConverter {

    @Override
    public TResumeAbilityRel saveDtoToEntity(ResumeAbilityRelSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        TResumeAbilityRel tResumeAbilityRel = new TResumeAbilityRel();

        tResumeAbilityRel.setResumeId( dto.getResumeId() );
        tResumeAbilityRel.setAbilityTagId( dto.getAbilityTagId() );
        tResumeAbilityRel.setConfidence( dto.getConfidence() );
        tResumeAbilityRel.setSource( dto.getSource() );

        return tResumeAbilityRel;
    }

    @Override
    public ResumeAbilityRelVO entityToVo(TResumeAbilityRel entity) {
        if ( entity == null ) {
            return null;
        }

        ResumeAbilityRelVO resumeAbilityRelVO = new ResumeAbilityRelVO();

        resumeAbilityRelVO.setId( entity.getId() );
        resumeAbilityRelVO.setResumeId( entity.getResumeId() );
        resumeAbilityRelVO.setAbilityTagId( entity.getAbilityTagId() );
        resumeAbilityRelVO.setConfidence( entity.getConfidence() );
        resumeAbilityRelVO.setSource( entity.getSource() );

        return resumeAbilityRelVO;
    }
}
