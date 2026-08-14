package org.example.hragent.converter;

import javax.annotation.processing.Generated;
import org.example.hragent.dto.ResumeSaveDto;
import org.example.hragent.dto.ResumeUpdateDto;
import org.example.hragent.entity.TResume;
import org.example.hragent.vo.ResumeVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-14T10:43:47+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class ResumeConverterImpl implements ResumeConverter {

    @Override
    public TResume saveDtoToEntity(ResumeSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        TResume tResume = new TResume();

        tResume.setResumeName( dto.getResumeName() );
        tResume.setGender( dto.getGender() );
        tResume.setBirthDate( dto.getBirthDate() );
        tResume.setPhone( dto.getPhone() );
        tResume.setEmail( dto.getEmail() );
        tResume.setIdCard( dto.getIdCard() );
        tResume.setExpectPosition( dto.getExpectPosition() );
        tResume.setExpectSalaryMin( dto.getExpectSalaryMin() );
        tResume.setExpectSalaryMax( dto.getExpectSalaryMax() );
        tResume.setExpectCity( dto.getExpectCity() );
        tResume.setWorkYears( dto.getWorkYears() );
        tResume.setEducation( dto.getEducation() );
        tResume.setSchool( dto.getSchool() );
        tResume.setMajor( dto.getMajor() );
        tResume.setResumeContent( dto.getResumeContent() );
        tResume.setResumeStructJson( dto.getResumeStructJson() );
        tResume.setResumeFileId( dto.getResumeFileId() );
        tResume.setResumeStatus( dto.getResumeStatus() );
        tResume.setDeliverySource( dto.getDeliverySource() );
        tResume.setTargetJobId( dto.getTargetJobId() );
        tResume.setMatchScore( dto.getMatchScore() );
        tResume.setScreeningOpinion( dto.getScreeningOpinion() );
        tResume.setOwnerEmpId( dto.getOwnerEmpId() );
        tResume.setRemark( dto.getRemark() );

        return tResume;
    }

    @Override
    public void updateDtoToEntity(ResumeUpdateDto dto, TResume entity) {
        if ( dto == null ) {
            return;
        }

        entity.setId( dto.getId() );
        entity.setResumeName( dto.getResumeName() );
        entity.setGender( dto.getGender() );
        entity.setBirthDate( dto.getBirthDate() );
        entity.setPhone( dto.getPhone() );
        entity.setEmail( dto.getEmail() );
        entity.setIdCard( dto.getIdCard() );
        entity.setExpectPosition( dto.getExpectPosition() );
        entity.setExpectSalaryMin( dto.getExpectSalaryMin() );
        entity.setExpectSalaryMax( dto.getExpectSalaryMax() );
        entity.setExpectCity( dto.getExpectCity() );
        entity.setWorkYears( dto.getWorkYears() );
        entity.setEducation( dto.getEducation() );
        entity.setSchool( dto.getSchool() );
        entity.setMajor( dto.getMajor() );
        entity.setResumeContent( dto.getResumeContent() );
        entity.setResumeStructJson( dto.getResumeStructJson() );
        entity.setResumeFileId( dto.getResumeFileId() );
        entity.setResumeStatus( dto.getResumeStatus() );
        entity.setDeliverySource( dto.getDeliverySource() );
        entity.setTargetJobId( dto.getTargetJobId() );
        entity.setMatchScore( dto.getMatchScore() );
        entity.setScreeningOpinion( dto.getScreeningOpinion() );
        entity.setOwnerEmpId( dto.getOwnerEmpId() );
        entity.setRemark( dto.getRemark() );
    }

    @Override
    public ResumeVO entityToVo(TResume entity) {
        if ( entity == null ) {
            return null;
        }

        ResumeVO resumeVO = new ResumeVO();

        resumeVO.setId( entity.getId() );
        resumeVO.setResumeName( entity.getResumeName() );
        resumeVO.setGender( entity.getGender() );
        resumeVO.setBirthDate( entity.getBirthDate() );
        resumeVO.setPhone( entity.getPhone() );
        resumeVO.setEmail( entity.getEmail() );
        resumeVO.setExpectPosition( entity.getExpectPosition() );
        resumeVO.setExpectSalaryMin( entity.getExpectSalaryMin() );
        resumeVO.setExpectSalaryMax( entity.getExpectSalaryMax() );
        resumeVO.setExpectCity( entity.getExpectCity() );
        resumeVO.setWorkYears( entity.getWorkYears() );
        resumeVO.setEducation( entity.getEducation() );
        resumeVO.setSchool( entity.getSchool() );
        resumeVO.setMajor( entity.getMajor() );
        resumeVO.setResumeContent( entity.getResumeContent() );
        resumeVO.setResumeFileId( entity.getResumeFileId() );
        resumeVO.setResumeStatus( entity.getResumeStatus() );
        resumeVO.setDeliverySource( entity.getDeliverySource() );
        resumeVO.setTargetJobId( entity.getTargetJobId() );
        resumeVO.setMatchScore( entity.getMatchScore() );
        resumeVO.setScreeningOpinion( entity.getScreeningOpinion() );
        resumeVO.setOwnerEmpId( entity.getOwnerEmpId() );
        resumeVO.setRemark( entity.getRemark() );

        return resumeVO;
    }
}
