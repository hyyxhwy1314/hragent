package org.example.hragent.controller;

import jakarta.validation.Valid;
import org.example.hragent.converter.BaseFullConverter;
import org.example.hragent.dto.BaseQueryDto;
import org.example.hragent.entity.BaseEntity;
import org.example.hragent.service.BaseService;
import org.example.hragent.vo.R;
import org.springframework.web.bind.annotation.*;

public abstract class BaseCrudController<T extends BaseEntity, V, Q extends BaseQueryDto, S, U>
        extends BaseController<T, V, Q> {

    protected abstract BaseFullConverter<T, V, S, U> fullConverter();

    @PostMapping
    public R<V> save(@Valid @RequestBody S saveDto) {
        T entity = fullConverter().saveDtoToEntity(saveDto);
        baseService().save(entity);
        return R.ok(fullConverter().entityToVo(entity));
    }

    @PutMapping("/{id}")
    public R<V> update(@PathVariable Long id, @Valid @RequestBody U updateDto) {
        T entity = baseService().getByIdChecked(id);
        fullConverter().updateDtoToEntity(updateDto, entity);
        baseService().updateById(entity);
        return R.ok(fullConverter().entityToVo(entity));
    }
}
