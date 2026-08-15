package org.example.hragent.converter;

import org.example.hragent.entity.BaseEntity;

import java.util.Collections;
import java.util.List;

public interface BaseConverter<T extends BaseEntity, V> {

    V entityToVo(T entity);

    default List<V> entityListToVoList(List<T> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::entityToVo).toList();
    }
}
