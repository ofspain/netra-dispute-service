package com.netstra.disputes.config;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.CacheKeyPrefix;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;


@Slf4j
@Configuration
@EnableCaching
public class CachingConfig extends CachingConfigurerSupport {

    private static final String API_PREFIX = "dispute-service";
    private static final String SEPARATOR = ":";
    @Value("${application.env:env}")
    private String environment;

    @Value("${acl.redis.key.prefix:nestra}")
    private String aclKeyPrefix;


    @Bean
    public RedisCacheManager cacheManager(LettuceConnectionFactory lettuceConnectionFactory) {
        var prefix = StringUtils.join(aclKeyPrefix, SEPARATOR, API_PREFIX, SEPARATOR, environment);
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(CacheKeyPrefix.prefixed(prefix))
                .disableCachingNullValues();

        return RedisCacheManager.builder(lettuceConnectionFactory)
                .cacheDefaults(cacheConfig)
                .build();
    }

    private RedisCacheConfiguration myDefaultCacheConfig(Duration duration) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(JsonGenerator.Feature.IGNORE_UNKNOWN);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.registerModule(new ParameterNamesModule());
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        //objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        objectMapper.activateDefaultTyping(objectMapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        return RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(duration)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler();
    }
}

