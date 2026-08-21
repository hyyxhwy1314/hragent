package org.example.hragent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.AbilityTag;
import org.example.hragent.mapper.AbilityTagMapper;
import org.example.hragent.service.DictionaryService;
import org.example.hragent.utils.RedisUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 字典数据服务实现
 *
 * 热点数据缓存策略：
 *
 * 1. 缓存穿透防护：
 *    - 查询 DB 无结果时，缓存一个空集合（TTL 60 秒）
 *    - 避免恶意请求反复打 DB
 *
 * 2. 缓存击穿防护：
 *    - 缓存失效瞬间，只让一个线程查 DB 重建缓存
 *    - 使用 Redisson 分布式锁，其他线程等待或返回旧值
 *
 * 3. 缓存雪崩防护：
 *    - TTL 加随机抖动（30 ± 5 分钟），避免同时失效
 */
@Slf4j
@Service
public class DictionaryServiceImpl implements DictionaryService {

    private static final String CACHE_KEY_LIST = "dict:ability_tag:list";
    private static final String CACHE_KEY_CATEGORY_PREFIX = "dict:ability_tag:category:";
    private static final String LOCK_KEY_REBUILD_PREFIX = "lock:dict:rebuild:";
    private static final long CACHE_TTL_MINUTES = 30;
    private static final long EMPTY_CACHE_TTL_SECONDS = 60;
    private static final long LOCK_WAIT_SECONDS = 3;
    private static final long LOCK_LEASE_SECONDS = 30;

    @Autowired
    private AbilityTagMapper abilityTagMapper;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public List<AbilityTag> listAbilityTags() {
        // 1. 先查缓存
        Object cached = redisUtils.get(CACHE_KEY_LIST);
        if (cached != null) {
            log.debug("字典缓存命中 list");
            return toList(cached);
        }

        // 2. 缓存未命中，加锁重建（防击穿）
        return rebuildWithLock(CACHE_KEY_LIST, () -> {
            // 双重检查：进入锁后再次确认
            Object doubleCheck = redisUtils.get(CACHE_KEY_LIST);
            if (doubleCheck != null) {
                return toList(doubleCheck);
            }

            // 查数据库
            List<AbilityTag> dbData = abilityTagMapper.selectList(
                    new LambdaQueryWrapper<AbilityTag>().orderByAsc(AbilityTag::getSort));

            // 写缓存（含空值穿透防护 + TTL 雪崩抖动）
            if (dbData == null || dbData.isEmpty()) {
                redisUtils.set(CACHE_KEY_LIST, Collections.emptyList(), EMPTY_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
                log.warn("字典查询为空，写入空值缓存防穿透 key={}", CACHE_KEY_LIST);
                return Collections.emptyList();
            }

            long ttl = ttlWithJitter();
            redisUtils.set(CACHE_KEY_LIST, dbData, ttl, TimeUnit.MINUTES);
            log.info("字典缓存重建完成 list size={} ttl={}min", dbData.size(), ttl);
            return dbData;
        });
    }

    @Override
    public List<AbilityTag> listAbilityTagsByCategory(String category) {
        String key = CACHE_KEY_CATEGORY_PREFIX + category;

        Object cached = redisUtils.get(key);
        if (cached != null) {
            return toList(cached);
        }

        return rebuildWithLock(key, () -> {
            Object doubleCheck = redisUtils.get(key);
            if (doubleCheck != null) {
                return toList(doubleCheck);
            }

            List<AbilityTag> dbData = abilityTagMapper.selectList(
                    new LambdaQueryWrapper<AbilityTag>()
                            .eq(AbilityTag::getTagCategory, category)
                            .orderByAsc(AbilityTag::getSort));

            if (dbData == null || dbData.isEmpty()) {
                redisUtils.set(key, Collections.emptyList(), EMPTY_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
                return Collections.emptyList();
            }

            redisUtils.set(key, dbData, ttlWithJitter(), TimeUnit.MINUTES);
            return dbData;
        });
    }

    @Override
    public void refreshCache() {
        evictCache();
        listAbilityTags();  // 立即预热
        log.info("字典缓存已主动刷新");
    }

    @Override
    public void evictCache() {
        redisUtils.delete(CACHE_KEY_LIST);
        // 清理分类缓存（通配符）
        Set<String> keys = redisUtils.keys(CACHE_KEY_CATEGORY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisUtils.delete(keys);
        }
        log.info("字典缓存已清空");
    }

    // ============================ 内部工具 ============================

    /**
     * 加分布式锁重建缓存（防击穿）
     */
    private List<AbilityTag> rebuildWithLock(String cacheKey, java.util.function.Supplier<List<AbilityTag>> loader) {
        RLock lock = redissonClient.getLock(LOCK_KEY_REBUILD_PREFIX + cacheKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                // 等待锁超时，直接查 DB 降级（保证可用性）
                log.warn("字典缓存重建锁等待超时，降级直查 DB key={}", cacheKey);
                return loader.get();
            }
            return loader.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("字典缓存重建被中断 key={}", cacheKey);
            return loader.get();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * TTL 加随机抖动，防止缓存雪崩
     * 基准 30 分钟 ± 5 分钟
     */
    private long ttlWithJitter() {
        long base = CACHE_TTL_MINUTES;
        long jitter = (long) (Math.random() * 10 - 5);
        return base + jitter;
    }

    @SuppressWarnings("unchecked")
    private List<AbilityTag> toList(Object obj) {
        if (obj == null) return Collections.emptyList();
        if (obj instanceof List) return (List<AbilityTag>) obj;
        // Jackson 反序列化可能是单对象
        return Collections.singletonList((AbilityTag) obj);
    }
}
