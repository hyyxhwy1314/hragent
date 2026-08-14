package org.example.hragent.converter;

import javax.annotation.processing.Generated;
import org.example.hragent.dto.JobPostSaveDto;
import org.example.hragent.dto.JobPostUpdateDto;
import org.example.hragent.entity.TJobPost;
import org.example.hragent.vo.JobPostVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-14T10:43:47+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class JobPostConverterImpl implements JobPostConverter {

    @Override
    public TJobPost saveDtoToEntity(JobPostSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        TJobPost tJobPost = new TJobPost();

        tJobPost.setJobCode( dto.getJobCode() );
        tJobPost.setJobName( dto.getJobName() );
        tJobPost.setDeptName( dto.getDeptName() );
        tJobPost.setWorkCity( dto.getWorkCity() );
        tJobPost.setWorkAddress( dto.getWorkAddress() );
        tJobPost.setJobDuty( dto.getJobDuty() );
        tJobPost.setJobRequirement( dto.getJobRequirement() );
        tJobPost.setSalaryMin( dto.getSalaryMin() );
        tJobPost.setSalaryMax( dto.getSalaryMax() );
        tJobPost.setEducationReq( dto.getEducationReq() );
        tJobPost.setWorkYearReq( dto.getWorkYearReq() );
        tJobPost.setHeadCount( dto.getHeadCount() );
        tJobPost.setJobStatus( dto.getJobStatus() );
        tJobPost.setIsPublic( dto.getIsPublic() );
        tJobPost.setPublishTime( dto.getPublishTime() );
        tJobPost.setCloseTime( dto.getCloseTime() );
        tJobPost.setCreatorEmpId( dto.getCreatorEmpId() );

        return tJobPost;
    }

    @Override
    public void updateDtoToEntity(JobPostUpdateDto dto, TJobPost entity) {
        if ( dto == null ) {
            return;
        }

        entity.setId( dto.getId() );
        entity.setJobCode( dto.getJobCode() );
        entity.setJobName( dto.getJobName() );
        entity.setDeptName( dto.getDeptName() );
        entity.setWorkCity( dto.getWorkCity() );
        entity.setWorkAddress( dto.getWorkAddress() );
        entity.setJobDuty( dto.getJobDuty() );
        entity.setJobRequirement( dto.getJobRequirement() );
        entity.setSalaryMin( dto.getSalaryMin() );
        entity.setSalaryMax( dto.getSalaryMax() );
        entity.setEducationReq( dto.getEducationReq() );
        entity.setWorkYearReq( dto.getWorkYearReq() );
        entity.setHeadCount( dto.getHeadCount() );
        entity.setJobStatus( dto.getJobStatus() );
        entity.setIsPublic( dto.getIsPublic() );
        entity.setPublishTime( dto.getPublishTime() );
        entity.setCloseTime( dto.getCloseTime() );
        entity.setCreatorEmpId( dto.getCreatorEmpId() );
    }

    @Override
    public JobPostVO entityToVo(TJobPost entity) {
        if ( entity == null ) {
            return null;
        }

        JobPostVO jobPostVO = new JobPostVO();

        jobPostVO.setId( entity.getId() );
        jobPostVO.setJobCode( entity.getJobCode() );
        jobPostVO.setJobName( entity.getJobName() );
        jobPostVO.setDeptName( entity.getDeptName() );
        jobPostVO.setWorkCity( entity.getWorkCity() );
        jobPostVO.setWorkAddress( entity.getWorkAddress() );
        jobPostVO.setJobDuty( entity.getJobDuty() );
        jobPostVO.setJobRequirement( entity.getJobRequirement() );
        jobPostVO.setSalaryMin( entity.getSalaryMin() );
        jobPostVO.setSalaryMax( entity.getSalaryMax() );
        jobPostVO.setEducationReq( entity.getEducationReq() );
        jobPostVO.setWorkYearReq( entity.getWorkYearReq() );
        jobPostVO.setHeadCount( entity.getHeadCount() );
        jobPostVO.setJobStatus( entity.getJobStatus() );
        jobPostVO.setIsPublic( entity.getIsPublic() );
        jobPostVO.setPublishTime( entity.getPublishTime() );
        jobPostVO.setCloseTime( entity.getCloseTime() );
        jobPostVO.setCreatorEmpId( entity.getCreatorEmpId() );

        return jobPostVO;
    }
}
