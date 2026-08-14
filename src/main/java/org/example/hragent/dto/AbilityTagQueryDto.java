package org.example.hragent.dto;

import lombok.Data;

@Data
public class AbilityTagQueryDto {
    private String tagName;
    private String tagCategory;
    private Integer status;
    private Long pageNum;
    private Long pageSize;
}