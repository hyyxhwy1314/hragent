package org.example.hragent.converter;

import javax.annotation.processing.Generated;
import org.example.hragent.dto.ResumeSaveDto;
import org.example.hragent.dto.ResumeUpdateDto;
import org.example.hragent.entity.Resume;
import org.example.hragent.vo.ResumeVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-19T16:58:49+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class ResumeConverterImpl implements ResumeConverter {

    @Override
    public Resume saveDtoToEntity(ResumeSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        Resume resume = new Resume();

        resume.setResumeName( dto.getResumeName() );
        resume.setGender( dto.getGender() );
        resume.setBirthDate( dto.getBirthDate() );
        resume.setPhone( dto.getPhone() );
        resume.setEmail( dto.getEmail() );
        resume.setIdCard( dto.getIdCard() );
        resume.setExpectPosition( dto.getExpectPosition() );
        resume.setExpectSalaryMin( dto.getExpectSalaryMin() );
        resume.setExpectSalaryMax( dto.getExpectSalaryMax() );
        resume.setExpectCity( dto.getExpectCity() );
        resume.setWorkYears( dto.getWorkYears() );
        resume.setEducation( dto.getEducation() );
        resume.setSchool( dto.getSchool() );
        resume.setMajor( dto.getMajor() );
        resume.setResumeContent( dto.getResumeContent() );
        resume.setResumeStructJson( dto.getResumeStructJson() );
        resume.setResumeFileId( dto.getResumeFileId() );
        resume.setResumeStatus( dto.getResumeStatus() );
        resume.setDeliverySource( dto.getDeliverySource() );
        resume.setTargetJobId( dto.getTargetJobId() );
        resume.setMatchScore( dto.getMatchScore() );
        resume.setScreeningOpinion( dto.getScreeningOpinion() );
        resume.setOwnerEmpId( dto.getOwnerEmpId() );
        resume.setRemark( dto.getRemark() );

        return resume;
    }

    @Override
    public void updateDtoToEntity(ResumeUpdateDto dto, Resume entity) {
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
    public ResumeVO entityToVo(Resume entity) {
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
        resumeVO.setIdCard( entity.getIdCard() );
        resumeVO.setExpectPosition( entity.getExpectPosition() );
        resumeVO.setExpectSalaryMin( entity.getExpectSalaryMin() );
        resumeVO.setExpectSalaryMax( entity.getExpectSalaryMax() );
        resumeVO.setExpectCity( entity.getExpectCity() );
        resumeVO.setWorkYears( entity.getWorkYears() );
        resumeVO.setEducation( entity.getEducation() );
        resumeVO.setSchool( entity.getSchool() );
        resumeVO.setMajor( entity.getMajor() );
        resumeVO.setResumeContent( entity.getResumeContent() );
        resumeVO.setResumeStructJson( entity.getResumeStructJson() );
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
