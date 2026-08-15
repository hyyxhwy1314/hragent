package org.example.hragent.dto;
import lombok.Data;

@Data
public class QueryDto {

    /**
     * 页码
     */
    private Long pageNum;

    /**
     * 每页条数
     */
    private Long pageSize;
}