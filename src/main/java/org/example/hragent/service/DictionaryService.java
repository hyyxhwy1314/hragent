package org.example.hragent.service;

import org.example.hragent.entity.AbilityTag;

import java.util.List;

/**
 * 字典数据服务
 * 封装热点数据缓存策略：防止缓存穿透、缓存击穿
 *
 * 三大策略：
 * 1. 全量列表缓存：减少 DB 查询，TTL 30 分钟
 * 2. 穿透防护：空值缓存短 TTL，避免恶意请求穿透到 DB
 * 3. 击穿防护：使用 Redisson 分布式锁保护"缓存重建"过程
 */
public interface DictionaryService {

    /**
     * 获取能力标签全量列表（带缓存）
     * 缓存命中：直接返回
     * 缓存未命中：加分布式锁 → 查 DB → 回写缓存
     */
    List<AbilityTag> listAbilityTags();

    /**
     * 按分类获取能力标签
     */
    List<AbilityTag> listAbilityTagsByCategory(String category);

    /**
     * 主动刷新缓存（管理后台/定时任务调用）
     */
    void refreshCache();

    /**
     * 清空字典缓存（写操作触发）
     */
    void evictCache();
}
