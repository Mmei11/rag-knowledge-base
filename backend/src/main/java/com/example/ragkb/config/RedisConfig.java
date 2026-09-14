package com.example.ragkb.config;

import com.example.ragkb.common.cache.CacheNames;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonRedisSerializer()));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(Map.of(
                        CacheNames.KNOWLEDGE_BASE_LIST, defaultConfig.entryTtl(Duration.ofMinutes(5)),
                        CacheNames.KNOWLEDGE_BASE_DETAIL, defaultConfig.entryTtl(Duration.ofMinutes(10)),
                        CacheNames.DOCUMENT_LIST, defaultConfig.entryTtl(Duration.ofMinutes(3)),
                        CacheNames.DOCUMENT_DETAIL, defaultConfig.entryTtl(Duration.ofMinutes(5)),
                        CacheNames.DOCUMENT_STATUS, defaultConfig.entryTtl(Duration.ofSeconds(30))))
                .transactionAware()
                .build();
    }

    static GenericJackson2JsonRedisSerializer jsonRedisSerializer() {
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.ragkb")
                .allowIfSubType("java.util")
                .allowIfSubType("java.time")
                .allowIfSubType("java.lang")
                .build();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                typeValidator,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer.registerNullValueSerializer(objectMapper, "@class");
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
