package org.example.hragent.converter;

import org.example.hragent.entity.BaseEntity;
import org.mapstruct.MappingTarget;

public interface BaseFullConverter<T extends BaseEntity, V, S, U> extends BaseConverter<T, V> {

    T saveDtoToEntity(S dto);

    void updateDtoToEntity(U dto, @MappingTarget T entity);
}
