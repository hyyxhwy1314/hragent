package org.example.hragent.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AbilityTagUpdateDto {

    @NotNull(message = "id不能为空")
    private Long id;

    private String tagCode;
    private String tagName;
    private String tagCategory;
    private Integer sort;
    private Integer status;
}
