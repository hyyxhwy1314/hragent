package org.example.hragent.mapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.hragent.entity.JobPost;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface JobPostMapper extends BaseMapper<JobPost> {
}
