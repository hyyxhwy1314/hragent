package org.example.hragent.dto;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
@Data
public class AbilityTagUpdateDto {
    @NotNull
    private Long id;
    private String tagName;
    private String tagCode;
    private String tagCategory;
    private Integer sort;
    private Integer status;
}
