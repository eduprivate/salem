package com.edu.salem.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

import static org.springframework.data.redis.cache.RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory;
import static org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair.fromSerializer;

@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${management.redis.hostname}")
    private String host;

    @Value("${management.redis.port}")
    private Integer port;

    @Value("${management.redis.timeout}")
    private int timeout;

    @Value("${management.redis.cache.ttlSeconds:60}")
    private int cacheTTLSeconds;

    @Value("${management.redis.cache.enabled:false}")
    private boolean cacheEnabled;

    @Bean
    public RedissonClient initRedisson() {
        org.redisson.config.Config config = new org.redisson.config.Config();
        String urlTemplate = "redis://%s:%s";
        config.useSingleServer().setTimeout(timeout).setAddress(String.format(urlTemplate, host, port));
        return Redisson.create(config);
    }

    @Bean
    public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redisson) {
        return new RedissonConnectionFactory(redisson);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        setSerializer(template);
        template.afterPropertiesSet();
        return template;
    }

    private RedisSerializer<Object> redisSerializer() {

        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(om,
                Object.class);
        return jackson2JsonRedisSerializer;
    }

    private void setSerializer(StringRedisTemplate template) {
        template.setValueSerializer(redisSerializer());
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        if (cacheEnabled) {
            return fromConnectionFactory(redisConnectionFactory)
                    .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
                            .disableCachingNullValues()
                            .entryTtl(Duration.ofSeconds(cacheTTLSeconds))
                            .serializeValuesWith(fromSerializer(redisSerializer())))
                    .build();
        } else {
            return new NoOpCacheManager();
        }
    }
}
