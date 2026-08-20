package org.example.hragent.service.impl;

import org.example.hragent.entity.AbilityTag;
import org.example.hragent.mapper.AbilityTagMapper;
import org.example.hragent.service.AbilityTagService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;

/**
 * 能力标签 Service 实现
 * 字典表：读多写少、变更频率极低，适合全量缓存
 * 缓存策略：getById + list 都缓存，写操作清空所有缓存
 */
@Service
public class AbilityTagServiceImpl extends BaseServiceImpl<AbilityTagMapper, AbilityTag> implements AbilityTagService {

    /**
     * 查询单条：按 id 缓存，命中则不查库
     */
    @Override
    @Cacheable(value = "ability_tag", key = "#id", unless = "#result == null")
    public AbilityTag getById(Serializable id) {
        return super.getById(id);
    }

    /**
     * 全量列表：整表缓存，key 固定为 'list'
     */
    @Override
    @Cacheable(value = "ability_tag", key = "'list'")
    public List<AbilityTag> list() {
        return super.list();
    }

    /**
     * 新增：清空该模块所有缓存
     */
    @Override
    @CacheEvict(value = "ability_tag", allEntries = true)
    public boolean save(AbilityTag entity) {
        return super.save(entity);
    }

    /**
     * 修改：清空该模块所有缓存
     */
    @Override
    @CacheEvict(value = "ability_tag", allEntries = true)
    public boolean updateById(AbilityTag entity) {
        return super.updateById(entity);
    }

    /**
     * 删除：清空该模块所有缓存
     */
    @Override
    @CacheEvict(value = "ability_tag", allEntries = true)
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }
}
