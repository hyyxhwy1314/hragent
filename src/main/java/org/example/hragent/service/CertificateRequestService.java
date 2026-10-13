package org.example.hragent.service;

import org.example.hragent.entity.CertificateRequest;

/**
 * 证明申请 Service
 */
public interface CertificateRequestService extends BaseService<CertificateRequest> {

    /**
     * 创建证明申请单并发起审批流程（直属上级 → HR 两级审批），返回已回填流程实例ID的申请单。
     *
     * @param empId    申请员工ID
     * @param certType 证明类型（在职证明/收入证明）
     * @param purpose  用途说明（可空）
     */
    CertificateRequest createAndStart(Long empId, String certType, String purpose);
}
