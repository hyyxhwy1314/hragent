package org.example.hragent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 配置类
 * 1. 配置 RedisTemplate<String, Object> 用于手动操作 Redis
 * 2. 配置 RedisCacheManager 用于 Spring Cache（@Cacheable/@CacheEvict）
 *    ⚠️ 不配 CacheManager 的话，@Cacheable 默认用 JdkSerializationRedisSerializer，
 *       对未实现 Serializable 的对象（如 R<T>、PageVO<T>）会抛 Cannot serialize
 *
 * 全局统一序列化策略：RedisTemplate 与 CacheManager 的 value 都使用同一个
 * GenericJackson2JsonRedisSerializer（注册 JavaTimeModule 支持 LocalDateTime，
 * 并开启默认类型信息，保证 List<POJO> 读回能还原为原类型，不会退化成 LinkedHashMap）。
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * 统一 JSON 序列化器：JavaTimeModule + 默认类型信息。
     * 唯一序列化配置源，供 RedisTemplate 与 CacheManager 共用。
     */
    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);
        return new GenericJackson2JsonRedisSerializer(om);
    }

    /**
     * 手动操作 Redis 用（RedisTemplate<String, Object>）
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        GenericJackson2JsonRedisSerializer valueSerializer = jsonSerializer();
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Spring Cache 用（@Cacheable/@CacheEvict 走这里）
     * 使用与 RedisTemplate 相同的统一 JSON 序列化器，保证缓存读写格式一致
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer valueSerializer = jsonSerializer();

        // 默认缓存配置：30 分钟 TTL + String Key + JSON Value
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }
}