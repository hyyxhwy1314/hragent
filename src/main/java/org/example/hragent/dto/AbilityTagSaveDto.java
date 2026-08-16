package org.example.hragent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AbilityTagSaveDto {

    @NotBlank(message = "标签编码不能为空")
    private String tagCode;

    @NotBlank(message = "标签名称不能为空")
    private String tagName;

    private String tagCategory;

    private Integer sort;

    /**
     * 0禁用 1启用
     */
    private Integer status;
}
