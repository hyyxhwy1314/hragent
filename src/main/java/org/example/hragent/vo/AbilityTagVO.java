package org.example.hragent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AbilityTagVO {
    private Long id;
    private String tagCode;
    private String tagName;
    private String tagCategory;
    private Integer sort;
    private Integer status;
    private String createTime;
    private String updateTime;
}
