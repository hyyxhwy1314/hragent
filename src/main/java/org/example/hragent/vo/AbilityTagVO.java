package org.example.hragent.vo;

import lombok.Data;

@Data
public class AbilityTagVO {
    private Long id;
    private String tagName;
    private String tagCode;
    private String tagCategory;
    private Integer sort;
    private Integer status;
}