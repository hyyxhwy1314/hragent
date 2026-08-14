package org.example.hragent.vo;

import lombok.Data;

import java.util.List;

@Data
public class PageVO<T> {
    private List<T> records;
    private Long total;
    private Long pageNum;
    private Long pageSize;

    public static <T> PageVO<T> of(List<T> records, Long total, Long pageNum, Long pageSize) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(records);
        vo.setTotal(total);
        vo.setPageNum(pageNum);
        vo.setPageSize(pageSize);
        return vo;
    }
}