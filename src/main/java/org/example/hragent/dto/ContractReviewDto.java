package org.example.hragent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 合同审查入参 DTO
 */
@Data
public class ContractReviewDto {
    /** 合同全文，必填 */
    @NotBlank(message = "合同内容不能为空")
    private String content;
    /** 合同名称（可选，用于在提示词中说明合同类型） */
    private String contractName;
}