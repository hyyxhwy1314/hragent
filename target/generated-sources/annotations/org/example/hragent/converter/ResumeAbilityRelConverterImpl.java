package org.example.hragent.converter;

import javax.annotation.processing.Generated;
import org.example.hragent.dto.ResumeAbilityRelSaveDto;
import org.example.hragent.dto.ResumeAbilityRelUpdateDto;
import org.example.hragent.entity.ResumeAbilityRel;
import org.example.hragent.vo.ResumeAbilityRelVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-19T16:58:49+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class ResumeAbilityRelConverterImpl implements ResumeAbilityRelConverter {

    @Override
    public ResumeAbilityRel saveDtoToEntity(ResumeAbilityRelSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        ResumeAbilityRel resumeAbilityRel = new ResumeAbilityRel();

        resumeAbilityRel.setResumeId( dto.getResumeId() );
        resumeAbilityRel.setAbilityTagId( dto.getAbilityTagId() );
        resumeAbilityRel.setConfidence( dto.getConfidence() );
        resumeAbilityRel.setSource( dto.getSource() );

        return resumeAbilityRel;
    }

    @Override
    public void updateDtoToEntity(ResumeAbilityRelUpdateDto dto, ResumeAbilityRel entity) {
        if ( dto == null ) {
            return;
        }

        entity.setId( dto.getId() );
        entity.setResumeId( dto.getResumeId() );
        entity.setAbilityTagId( dto.getAbilityTagId() );
        entity.setConfidence( dto.getConfidence() );
        entity.setSource( dto.getSource() );
    }

    @Override
    public ResumeAbilityRelVO entityToVo(ResumeAbilityRel entity) {
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
