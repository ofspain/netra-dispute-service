package com.netstra.disputes.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableRedisRepositories(basePackages = "com.netstra.disputes")
@Slf4j
public class RedisConfig {


    @Value("${spring.data.redis.max-redirects}")
    private int maxRedirects;

    @Value("${spring.data.redis.timeout:1000}")
    private long timeout;

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

//
//    @Bean
//    public LettuceConnectionFactory lettuceConnectionFactory() {
//        GenericObjectPoolConfig genericObjectPoolConfig = new GenericObjectPoolConfig();
//        genericObjectPoolConfig.setMaxIdle(maxIdle);
//        genericObjectPoolConfig.setMinIdle(minIdle);
//        genericObjectPoolConfig.setMaxTotal(maxActive);
//        genericObjectPoolConfig.setMaxWaitMillis(maxWait);
//        genericObjectPoolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
//        String[] nodes = clusterNodes.split(",");
//        List<RedisNode> listNodes = new ArrayList<>();
//        for (String node : nodes) {
//            String[] ipAndPort = node.split(":");
//            RedisNode redisNode = new RedisNode(ipAndPort[0], Integer.parseInt(ipAndPort[1]));
//            listNodes.add(redisNode);
//        }
//        RedisClusterConfiguration redisClusterConfiguration = new RedisClusterConfiguration();
//        redisClusterConfiguration.setClusterNodes(listNodes);
//        redisClusterConfiguration.setMaxRedirects(maxRedirects);
//        // Configure automated topology refresh.
//        ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
//                .enablePeriodicRefresh(Duration.ofSeconds(clusterRefreshPeriod)) // Refresh the topology periodically.
//                .enableAllAdaptiveRefreshTriggers() // Refresh the topology based on events.
//                .build();
//
//        ClusterClientOptions clusterClientOptions = ClusterClientOptions.builder()
//                // Redis command execution timeout. Only when the command execution times out will a reconnection be triggered using the new topology.
//                .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(clusterRefreshPeriod)))
//                .topologyRefreshOptions(topologyRefreshOptions)
//                .build();
//        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
//                .commandTimeout(Duration.ofSeconds(timeout))
//                .poolConfig(genericObjectPoolConfig)
//                //.readFrom(ReadFrom.REPLICA_PREFERRED) // Preferentially read data from the replicas.
//                .clientOptions(clusterClientOptions)
//                .build();
//        return new LettuceConnectionFactory(redisClusterConfiguration, clientConfig);
//    }


    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory(
            @Value("${spring.data.redis.nodes}") String clusterNodes,
            @Value("${spring.data.redis.max-redirects}") int maxRedirects,
            @Value("${spring.data.redis.username}") String username,
            @Value("${spring.data.redis.password}") String password) {

        GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxTotal(maxActive);
        poolConfig.setMaxWaitMillis(maxWait);
        poolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);

        // Build cluster node list
        String[] nodes = clusterNodes.split(",");
        List<RedisNode> listNodes = new ArrayList<>();
        for (String node : nodes) {
            String[] ipAndPort = node.split(":");
            listNodes.add(new RedisNode(ipAndPort[0], Integer.parseInt(ipAndPort[1])));
        }

        // Cluster config + ACL authentication
        RedisClusterConfiguration redisClusterConfiguration = new RedisClusterConfiguration();
        redisClusterConfiguration.setClusterNodes(listNodes);
        redisClusterConfiguration.setMaxRedirects(maxRedirects);
        redisClusterConfiguration.setUsername(username);   // ACL username
        redisClusterConfiguration.setPassword(password);   // ACL password

        // Cluster topology refresh
        ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(Duration.ofSeconds(clusterRefreshPeriod))
                .enableAllAdaptiveRefreshTriggers()
                .build();

        ClusterClientOptions clusterClientOptions = ClusterClientOptions.builder()
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(clusterRefreshPeriod)))
                .topologyRefreshOptions(topologyRefreshOptions)
                .build();

        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(timeout))
                .poolConfig(poolConfig)
                .clientOptions(clusterClientOptions)
                .build();

        return new LettuceConnectionFactory(redisClusterConfiguration, clientConfig);
    }


    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(lettuceConnectionFactory);

        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        // Use Jackson2JsonRedisSerializer to replace the default JdkSerializationRedisSerializer to serialize and deserialize the Redis value.
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(mapper, Object.class);


        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        // String serialization of keys
        template.setKeySerializer(stringRedisSerializer);
        // String serialization of hash keys
        template.setHashKeySerializer(stringRedisSerializer);
        // Jackson serialization of values
        template.setValueSerializer(jackson2JsonRedisSerializer);
        // Jackson serialization of hash values
        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        template.afterPropertiesSet();
        return template;
    }

}
