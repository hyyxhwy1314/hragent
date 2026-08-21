package org.example.hragent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AbilityTagQueryDto extends BaseQueryDto {

    private String tagName;

    private String tagCategory;

    private Integer status;
}
