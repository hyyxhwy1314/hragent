package org.example.hragent.vo;
import lombok.Data;
import java.util.List;

/**
 * 分页通用返回视图对象
 * @param <T> 分页数据项类型
 */
@Data
public class PageVO<T> {

    /**
     * 当前页数据列表
     */
    private List<T> records;

    /**
     * 数据总条数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Long pageNum;

    /**
     * 每页条数
     */
    private Long pageSize;

    /**
     * 构建分页VO对象
     * @param records 当前页数据列表
     * @param total 数据总条数
     * @param pageNum 当前页码
     * @param pageSize 每页条数
     * @return 分页视图对象
     */
    public static <T> PageVO<T> of(List<T> records, Long total, Long pageNum, Long pageSize) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(records);
        vo.setTotal(total);
        vo.setPageNum(pageNum);
        vo.setPageSize(pageSize);
        return vo;
    }
}