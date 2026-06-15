package com.feiting.feiapi.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 缓存配置类
 * <p>
 * 生产环境使用 Redis 作为共享缓存，测试环境使用本地 ConcurrentHashMap 缓存
 * </p>
 *
 * @author feiting
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 登录用户缓存名称
     */
    public static final String LOGIN_USER_CACHE_NAME = "loginUserSnapshot";

    /**
     * 登录用户缓存 TTL（分钟）
     */
    public static final long LOGIN_USER_CACHE_TTL_MINUTES = 10;

    /**
     * 生产环境 Redis 缓存管理器
     * <p>
     * 使用 Redis 作为共享缓存，支持多实例部署
     * </p>
     *
     * @param connectionFactory Redis 连接工厂
     * @return Redis 缓存管理器
     */
    @Bean
    @Profile("!test")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // 配置序列化方式
        RedisSerializationContext.SerializationPair<Object> jsonSerializer =
                RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer());

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(LOGIN_USER_CACHE_TTL_MINUTES))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(jsonSerializer)
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * 测试环境本地缓存管理器
     * <p>
     * 使用 ConcurrentHashMap 作为本地缓存，避免测试环境依赖真实 Redis
     * </p>
     *
     * @return 本地缓存管理器
     */
    @Bean
    @Profile("test")
    public CacheManager concurrentMapCacheManager() {
        return new ConcurrentMapCacheManager(LOGIN_USER_CACHE_NAME);
    }

    /**
     * 测试环境专用的简单缓存管理器实现
     * <p>
     * 基于 ConcurrentHashMap，不依赖 Redis，适用于单元测试和集成测试
     * </p>
     */
    public static class ConcurrentMapCacheManager implements CacheManager {

        private final ConcurrentMap<String, org.springframework.cache.Cache> cacheMap = new ConcurrentHashMap<>();

        /**
         * 创建简单缓存管理器
         *
         * @param cacheNames 缓存名称列表
         */
        public ConcurrentMapCacheManager(String... cacheNames) {
            for (String name : cacheNames) {
                cacheMap.put(name, new org.springframework.cache.concurrent.ConcurrentMapCache(name));
            }
        }

        @Override
        public org.springframework.cache.Cache getCache(String name) {
            return cacheMap.computeIfAbsent(name,
                    key -> new org.springframework.cache.concurrent.ConcurrentMapCache(key));
        }

        @Override
        public Collection<String> getCacheNames() {
            return Collections.unmodifiableSet(cacheMap.keySet());
        }
    }
}
