package com.netstra.disputes.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.api.StatefulConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.nodes}")
    private String clusterNodes;

    @Value("${spring.data.redis.timeout:1000}")
    private long timeout;

    @Value("${spring.data.redis.max-redirects}")
    private int maxRedirects;

    @Value("${spring.data.redis.lettuce.pool.max-idle:50}")
    private int maxIdle;

    @Value("${spring.data.redis.lettuce.pool.min-idle:5}")
    private int minIdle;

    @Value("${spring.data.redis.lettuce.pool.max-active:50}")
    private int maxActive;

    @Value("${spring.data.redis.lettuce.pool.max-wait:-1}")
    private long maxWait;

    @Value("${redis.timeBetweenEvictionRunsMillis:300000}")
    private long timeBetweenEvictionRunsMillis;

    @Value("${spring.redis.lettuce.refresh.period:60}")
    private int clusterRefreshPeriod;

    // -------------------------------------------------------------------------
    // 1️⃣ Generic LettuceConnectionFactory for Redis cluster
    // -------------------------------------------------------------------------
    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {

        GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxTotal(maxActive);
        poolConfig.setMaxWaitMillis(maxWait);
        poolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);

        List<RedisNode> nodes = new ArrayList<>();
        for (String node : clusterNodes.split(",")) {
            String[] ipPort = node.split(":");
            nodes.add(new RedisNode(ipPort[0], Integer.parseInt(ipPort[1])));
        }

        RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration();
        clusterConfig.setClusterNodes(nodes);
        clusterConfig.setMaxRedirects(maxRedirects);

        // Cluster topology refresh options
        ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(Duration.ofSeconds(clusterRefreshPeriod))
                .enableAllAdaptiveRefreshTriggers()
                .build();

        ClusterClientOptions clusterClientOptions = ClusterClientOptions.builder()
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(clusterRefreshPeriod)))
                .topologyRefreshOptions(topologyRefreshOptions)
                .build();

        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(timeout))
                .poolConfig(poolConfig)
                .clientOptions(clusterClientOptions)
                .build();

        return new LettuceConnectionFactory(clusterConfig, clientConfig);
    }

    // -------------------------------------------------------------------------
    // 2️⃣ Generic RedisTemplate for any cache
    // -------------------------------------------------------------------------
    private RedisTemplate<String, Object> createRedisTemplate(LettuceConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Jackson serializer for values
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        Jackson2JsonRedisSerializer<Object> jacksonSerializer = new Jackson2JsonRedisSerializer<>(mapper, Object.class);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jacksonSerializer);
        template.setHashValueSerializer(jacksonSerializer);
        template.afterPropertiesSet();

        return template;
    }

    // -------------------------------------------------------------------------
    // 3️⃣ RedisTemplate bean for banned content cache (deterministic injection)
    // -------------------------------------------------------------------------
    @Bean(name = "bannedContentRedisTemplate")
    public RedisTemplate<String, Object> bannedContentRedisTemplate(LettuceConnectionFactory factory) {
        return createRedisTemplate(factory);
    }

    // -------------------------------------------------------------------------
    // 4️⃣ RedisTemplate bean for dispute cache
    // -------------------------------------------------------------------------
    @Bean(name = "disputeRedisTemplate")
    public RedisTemplate<String, Object> disputeRedisTemplate(LettuceConnectionFactory factory) {
        return createRedisTemplate(factory);
    }

    // -------------------------------------------------------------------------
    // 5️⃣ Add more caches by defining additional beans here
    // -------------------------------------------------------------------------
}
