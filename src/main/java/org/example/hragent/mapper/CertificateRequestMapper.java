package org.example.hragent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.hragent.entity.CertificateRequest;

/**
 * 证明申请 Mapper
 */
@Mapper
public interface CertificateRequestMapper extends BaseMapper<CertificateRequest> {
}
