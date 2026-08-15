package org.example.hragent.dto;

import lombok.Data;

@Data
public abstract class BaseQueryDto {

    private Long pageNum = 1L;

    private Long pageSize = 10L;
}
