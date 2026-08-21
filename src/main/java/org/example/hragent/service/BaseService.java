package org.example.hragent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.hragent.dto.BaseQueryDto;
import org.example.hragent.entity.BaseEntity;
import org.example.hragent.vo.PageVO;

public interface BaseService<T extends BaseEntity> extends IService<T> {

    Page<T> page(BaseQueryDto queryDto, LambdaQueryWrapper<T> wrapper);

    PageVO<T> pageVO(BaseQueryDto queryDto, LambdaQueryWrapper<T> wrapper);

    T getByIdChecked(Long id);

    boolean removeByIdChecked(Long id);

    T saveReturnEntity(T entity);
}
