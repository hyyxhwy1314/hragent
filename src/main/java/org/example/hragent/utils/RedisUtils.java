package org.example.hragent.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 通用工具类
 * 封装 RedisTemplate 常用操作，屏蔽掉繁琐的 opsForXxx 调用
 * 同时提供 StringRedisTemplate 用于计数器等纯字符串场景
 */
@Component
public class RedisUtils {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // ============================ String 操作 ============================

    /** 写入（永久） */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /** 写入 + 过期时间 */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /** 不存在才写入（SETNX），返回 true 表示抢到 */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
        return Boolean.TRUE.equals(ok);
    }

    /** 读取 */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /** 读取并指定类型（JSON 反序列化后通常是 LinkedHashMap） */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    /** 删除单个 key */
    public boolean delete(String key) {
        Boolean ok = redisTemplate.delete(key);
        return Boolean.TRUE.equals(ok);
    }

    /** 批量删除 */
    public long delete(Collection<String> keys) {
        Long n = redisTemplate.delete(keys);
        return n == null ? 0 : n;
    }

    /** 是否存在 */
    public boolean hasKey(String key) {
        Boolean ok = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(ok);
    }

    /** 设置过期时间 */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        Boolean ok = redisTemplate.expire(key, timeout, unit);
        return Boolean.TRUE.equals(ok);
    }

    /** 获取剩余过期时间（秒），-1 永久，-2 不存在 */
    public long getExpire(String key) {
        Long t = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return t == null ? -2 : t;
    }

    /** 自增（纯数字字符串计数器场景） */
    public long increment(String key) {
        Long v = stringRedisTemplate.opsForValue().increment(key);
        return v == null ? 0 : v;
    }

    /** 自增指定步长 */
    public long increment(String key, long delta) {
        Long v = stringRedisTemplate.opsForValue().increment(key, delta);
        return v == null ? 0 : v;
    }

    /** 自减 */
    public long decrement(String key) {
        Long v = stringRedisTemplate.opsForValue().decrement(key);
        return v == null ? 0 : v;
    }

    // ============================ Hash 操作 ============================

    public void hSet(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    public Object hGet(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    public void hSetAll(String key, Map<String, Object> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public long hDelete(String key, Object... hashKeys) {
        Long n = redisTemplate.opsForHash().delete(key, hashKeys);
        return n == null ? 0 : n;
    }

    public boolean hHasKey(String key, String hashKey) {
        Boolean ok = redisTemplate.opsForHash().hasKey(key, hashKey);
        return Boolean.TRUE.equals(ok);
    }

    // ============================ List 操作 ============================

    public long lPush(String key, Object value) {
        Long n = redisTemplate.opsForList().leftPush(key, value);
        return n == null ? 0 : n;
    }

    public Object lPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    public List<Object> lRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    // ============================ Set 操作 ============================

    public void sAdd(String key, Object... values) {
        redisTemplate.opsForSet().add(key, values);
    }

    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public boolean sIsMember(String key, Object value) {
        Boolean ok = redisTemplate.opsForSet().isMember(key, value);
        return Boolean.TRUE.equals(ok);
    }

    // ============================ 通配符查询 ============================

    /** 按通配符查询 key（生产慎用 KEYS，大库会阻塞） */
    public Set<String> keys(String pattern) {
        return redisTemplate.keys(pattern);
    }
}
