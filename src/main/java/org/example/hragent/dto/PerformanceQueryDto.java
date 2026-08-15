package org.example.hragent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PerformanceQueryDto extends BaseQueryDto {

    private Long empId;

    private String periodCode;

    private Integer status;
}
