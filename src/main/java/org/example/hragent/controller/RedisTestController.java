package org.example.hragent.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.hragent.annotation.DistributedLock;
import org.example.hragent.annotation.RateLimit;
import org.example.hragent.annotation.RepeatSubmit;
import org.example.hragent.entity.AbilityTag;
import org.example.hragent.service.DictionaryService;
import org.example.hragent.utils.RedisUtils;
import org.example.hragent.vo.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis / Redisson 组件测试接口
 *
 * 验收清单：
 * 1. GET  /redis/ping         —— 基础连通性
 * 2. GET  /redis/object       —— 对象序列化
 * 3. GET  /redis/lock          —— 分布式锁单线程
 * 4. GET  /redis/lock/concurrent —— 分布式锁并发测试
 * 5. GET  /redis/rate-limit    —— 限流测试（连续刷会触发 1007）
 * 6. POST /redis/repeat-submit —— 防重复提交测试
 * 7. GET  /redis/dict          —— 字典缓存读写
 */
@Slf4j
@RestController
@RequestMapping("/redis")
public class RedisTestController {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private DictionaryService dictionaryService;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    // ============================ AOP 诊断 ============================

    /**
     * 诊断 AOP 切面是否生效：打印当前 Controller 实际类名 + 所有 Aspect Bean
     */
    @GetMapping("/debug/aop")
    public R<Map<String, Object>> debugAop() {
        Map<String, Object> result = new HashMap<>();
        // 1. Controller 实际类名（被代理会变成 xxx$$EnhancerBySpringCGLIB...）
        result.put("controllerClass", this.getClass().getName());
        result.put("controllerIsProxy", this.getClass().getName().contains("$"));
        // 2. 列出所有 Aspect 类型的 Bean
        Map<String, Object> aspects = new HashMap<>();
        String[] aspectNames = applicationContext.getBeanNamesForType(Object.class);
        java.util.List<String> aspectBeans = new java.util.ArrayList<>();
        for (String name : aspectNames) {
            Class<?> t = applicationContext.getType(name);
            if (t != null && t.isAnnotationPresent(org.aspectj.lang.annotation.Aspect.class)) {
                aspectBeans.add(name + " -> " + t.getName());
            }
        }
        aspects.put("aspectBeans", aspectBeans);
        aspects.put("count", aspectBeans.size());
        result.put("aspects", aspects);
        return R.ok(result);
    }

    // ============================ 基础连通性 ============================

    @GetMapping("/ping")
    public R<Map<String, Object>> ping() {
        Map<String, Object> result = new HashMap<>();
        try {
            String value = "ping-" + System.currentTimeMillis();
            redisTemplate.opsForValue().set("test:ping", value, 60, TimeUnit.SECONDS);
            result.put("write", value);
            Object got = redisTemplate.opsForValue().get("test:ping");
            result.put("read", got);
            redisTemplate.delete("test:ping");
            result.put("status", "OK");
        } catch (Exception e) {
            result.put("status", "FAIL");
            result.put("error", e.getMessage());
        }
        return R.ok(result);
    }

    @GetMapping("/object")
    public R<Map<String, Object>> objectTest() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> user = new HashMap<>();
            user.put("id", 1);
            user.put("name", "张三");
            user.put("dept", "研发部");
            redisTemplate.opsForValue().set("test:obj", user, 60, TimeUnit.SECONDS);
            Object got = redisTemplate.opsForValue().get("test:obj");
            result.put("readBackType", got == null ? "null" : got.getClass().getSimpleName());
            result.put("status", "OK");
            redisTemplate.delete("test:obj");
        } catch (Exception e) {
            result.put("status", "FAIL");
            result.put("error", e.getMessage());
        }
        return R.ok(result);
    }

    // ============================ 分布式锁测试 ============================

    /**
     * 单线程加锁测试
     */
    @GetMapping("/lock")
    @DistributedLock(key = "'test:lock'", waitTime = 3, leaseTime = 10, message = "测试锁获取失败")
    public R<Map<String, Object>> lockTest() {
        Map<String, Object> result = new HashMap<>();
        result.put("thread", Thread.currentThread().getName());
        result.put("acquiredAt", System.currentTimeMillis());
        // 模拟业务执行 1 秒
        try { TimeUnit.SECONDS.sleep(1); } catch (InterruptedException ignored) {}
        result.put("releasedAt", System.currentTimeMillis());
        return R.ok(result);
    }

    /**
     * 并发加锁测试：10 个线程同时抢锁，应只有 1 个成功，其他 9 个超时失败
     * 调用方式：在浏览器/Postman 中连续访问 10 次此接口
     */
    @GetMapping("/lock/concurrent")
    @DistributedLock(key = "'test:concurrent'", waitTime = 2, leaseTime = 10,
            message = "并发测试：锁被占用，请稍后")
    public R<Map<String, Object>> concurrentLockTest() {
        Map<String, Object> result = new HashMap<>();
        result.put("thread", Thread.currentThread().getName());
        result.put("acquiredAt", System.currentTimeMillis());
        log.info("🔒 并发测试 - 线程 {} 获取到锁", Thread.currentThread().getName());
        // 持有锁 3 秒，让其他请求等不到锁
        try { TimeUnit.SECONDS.sleep(3); } catch (InterruptedException ignored) {}
        log.info("🔓 并发测试 - 线程 {} 释放锁", Thread.currentThread().getName());
        return R.ok(result);
    }

    // ============================ 限流测试 ============================

    /**
     * 限流测试：每 5 秒允许 2 个请求
     * 调用方式：快速连续访问 5 次，应只有前 2 次成功，后 3 次报 1007
     */
    @GetMapping("/rate-limit")
    @RateLimit(rate = 2, rateInterval = 5, message = "限流测试：5 秒内只允许 2 次")
    public R<String> rateLimitTest() {
        return R.ok("通过 " + System.currentTimeMillis());
    }

    // ============================ 防重复提交测试 ============================

    /**
     * 防重复提交测试：3 秒内相同参数视为重复
     */
    @PostMapping("/repeat-submit")
    @RepeatSubmit(interval = 3, unit = TimeUnit.SECONDS,
            keyType = RepeatSubmit.KeyType.PARAMS, message = "3 秒内请勿重复提交")
    public R<Map<String, Object>> repeatSubmitTest(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        result.put("submittedAt", System.currentTimeMillis());
        result.put("body", body);
        // 模拟业务处理
        try { TimeUnit.MILLISECONDS.sleep(500); } catch (InterruptedException ignored) {}
        return R.ok(result);
    }

    // ============================ 字典缓存测试 ============================

    /**
     * 字典缓存读取：第一次走 DB，第二次走缓存
     */
    @GetMapping("/dict")
    public R<Map<String, Object>> dictTest() {
        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();

        List<AbilityTag> tags = dictionaryService.listAbilityTags();

        result.put("size", tags.size());
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("hasCacheKey", redisUtils.hasKey("dict:ability_tag:list"));
        return R.ok(result);
    }

    /**
     * 主动刷新字典缓存
     */
    @PostMapping("/dict/refresh")
    public R<String> dictRefresh() {
        dictionaryService.refreshCache();
        return R.ok("字典缓存已刷新");
    }

    /**
     * 清空字典缓存
     */
    @DeleteMapping("/dict")
    public R<String> dictEvict() {
        dictionaryService.evictCache();
        return R.ok("字典缓存已清空");
    }

    // ============================ RedisUtils 测试 ============================

    /**
     * RedisUtils 工具类验证
     */
    @GetMapping("/utils")
    public R<Map<String, Object>> utilsTest() {
        Map<String, Object> result = new HashMap<>();
        try {
            // String 操作
            redisUtils.set("test:utils:str", "hello", 60, TimeUnit.SECONDS);
            result.put("str", redisUtils.get("test:utils:str"));

            // 自增
            redisUtils.delete("test:utils:counter");
            result.put("inc1", redisUtils.increment("test:utils:counter"));
            result.put("inc2", redisUtils.increment("test:utils:counter"));
            result.put("inc3", redisUtils.increment("test:utils:counter", 10));

            // Hash
            redisUtils.hSet("test:utils:hash", "k1", "v1");
            redisUtils.hSet("test:utils:hash", "k2", "v2");
            result.put("hash", redisUtils.hGetAll("test:utils:hash"));

            // exists / expire
            result.put("hasKey", redisUtils.hasKey("test:utils:hash"));
            result.put("expireSec", redisUtils.getExpire("test:utils:str"));

            // 清理
            redisUtils.delete(java.util.Arrays.asList(
                    "test:utils:str", "test:utils:counter", "test:utils:hash"));
            result.put("status", "OK");
        } catch (Exception e) {
            result.put("status", "FAIL");
            result.put("error", e.getMessage());
        }
        return R.ok(result);
    }
}
