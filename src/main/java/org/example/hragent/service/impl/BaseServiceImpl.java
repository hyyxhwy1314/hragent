package org.example.hragent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.hragent.dto.BaseQueryDto;
import org.example.hragent.entity.BaseEntity;
import org.example.hragent.exception.BusinessException;
import org.example.hragent.exception.ErrorCode;
import org.example.hragent.service.BaseService;
import org.example.hragent.vo.PageVO;

import java.util.List;

public abstract class BaseServiceImpl<M extends BaseMapper<T>, T extends BaseEntity>
        extends ServiceImpl<M, T> implements BaseService<T> {

    @Override
    public Page<T> page(BaseQueryDto queryDto, LambdaQueryWrapper<T> wrapper) {
        long pageNum = queryDto.getPageNum() == null ? 1L : queryDto.getPageNum();
        long pageSize = queryDto.getPageSize() == null ? 10L : queryDto.getPageSize();
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public PageVO<T> pageVO(BaseQueryDto queryDto, LambdaQueryWrapper<T> wrapper) {
        Page<T> page = page(queryDto, wrapper);
        return PageVO.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public T getByIdChecked(Long id) {
        T entity = this.getById(id);
        BusinessException.throwIf(entity == null, ErrorCode.DATA_NOT_FOUND);
        return entity;
    }

    @Override
    public boolean removeByIdChecked(Long id) {
        BusinessException.throwIf(this.getById(id) == null, ErrorCode.DATA_NOT_FOUND);
        return this.removeById(id);
    }

    @Override
    public T saveReturnEntity(T entity) {
        this.save(entity);
        return entity;
    }

    protected LambdaQueryWrapper<T> emptyWrapper() {
        return new LambdaQueryWrapper<>();
    }
}
