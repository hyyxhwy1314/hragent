package org.example.hragent.converter;

import javax.annotation.processing.Generated;
import org.example.hragent.dto.JobPostSaveDto;
import org.example.hragent.dto.JobPostUpdateDto;
import org.example.hragent.entity.JobPost;
import org.example.hragent.vo.JobPostVO;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-15T17:23:12+0800",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Oracle Corporation)"
)
@Component
public class JobPostConverterImpl implements JobPostConverter {

    @Override
    public JobPost saveDtoToEntity(JobPostSaveDto dto) {
        if ( dto == null ) {
            return null;
        }

        JobPost jobPost = new JobPost();

        jobPost.setJobCode( dto.getJobCode() );
        jobPost.setJobName( dto.getJobName() );
        jobPost.setDeptName( dto.getDeptName() );
        jobPost.setWorkCity( dto.getWorkCity() );
        jobPost.setWorkAddress( dto.getWorkAddress() );
        jobPost.setJobDuty( dto.getJobDuty() );
        jobPost.setJobRequirement( dto.getJobRequirement() );
        jobPost.setSalaryMin( dto.getSalaryMin() );
        jobPost.setSalaryMax( dto.getSalaryMax() );
        jobPost.setEducationReq( dto.getEducationReq() );
        jobPost.setWorkYearReq( dto.getWorkYearReq() );
        jobPost.setHeadCount( dto.getHeadCount() );
        jobPost.setJobStatus( dto.getJobStatus() );
        jobPost.setIsPublic( dto.getIsPublic() );
        jobPost.setPublishTime( dto.getPublishTime() );
        jobPost.setCloseTime( dto.getCloseTime() );
        jobPost.setCreatorEmpId( dto.getCreatorEmpId() );

        return jobPost;
    }

    @Override
    public void updateDtoToEntity(JobPostUpdateDto dto, JobPost entity) {
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
    public JobPostVO entityToVo(JobPost entity) {
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
