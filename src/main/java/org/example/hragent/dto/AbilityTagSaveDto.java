package org.example.hragent.dto;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class AbilityTagSaveDto {
    @NotNull
    private String tagName;

    @NotNull
    private String tagCode;

    private String tagCategory;

    private Integer sort;

    private Integer status;
}
