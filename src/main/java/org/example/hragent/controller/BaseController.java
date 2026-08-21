package org.example.hragent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.hragent.converter.BaseConverter;
import org.example.hragent.dto.BaseQueryDto;
import org.example.hragent.entity.BaseEntity;
import org.example.hragent.service.BaseService;
import org.example.hragent.vo.PageVO;
import org.example.hragent.vo.R;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

public abstract class BaseController<T extends BaseEntity, V, Q extends BaseQueryDto> {

    protected abstract BaseService<T> baseService();

    protected abstract BaseConverter<T, V> baseConverter();

    protected LambdaQueryWrapper<T> buildWrapper(Q queryDto) {
        return new LambdaQueryWrapper<>();
    }

    @GetMapping("/page")
    public R<PageVO<V>> page(Q queryDto) {
        PageVO<T> pageVO = baseService().pageVO(queryDto, buildWrapper(queryDto));
        PageVO<V> result = PageVO.of(
                baseConverter().entityListToVoList(pageVO.getRecords()),
                pageVO.getTotal(),
                pageVO.getPageNum(),
                pageVO.getPageSize()
        );
        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<V> getById(@PathVariable Long id) {
        return R.ok(baseConverter().entityToVo(baseService().getByIdChecked(id)));
    }

    @GetMapping("/list")
    public R<List<V>> list() {
        return R.ok(baseConverter().entityListToVoList(baseService().list()));
    }

    @DeleteMapping("/{id}")
    public R<Boolean> delete(@PathVariable Long id) {
        return R.ok(baseService().removeByIdChecked(id));
    }
}
