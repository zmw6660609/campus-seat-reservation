package com.sr.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        return template;
    }

    /**
     * 给 Redis 的 JSON 序列化器专门配一个 ObjectMapper。
     *
     * 不能直接用默认的：new GenericJackson2JsonRedisSerializer() 内部那个 ObjectMapper
     * 没有注册 Java 8 时间模块。M2 往 Redis 里存登录态时，值只有字符串和数字，这个问题
     * 一直藏着；到 M3-5 缓存座位列表才炸出来 —— Seat 实体带 createTime / updateTime
     * (LocalDateTime)，序列化直接抛：
     *   InvalidDefinitionException: Java 8 date/time type `java.time.LocalDateTime` not supported
     * 结果就是缓存写不进去，查空座接口整个 500。
     *
     * 这里要补两件事：
     * 1) 注册 JavaTimeModule，让 LocalDateTime 能进能出；顺手关掉时间戳数组写法，按 ISO-8601 存字符串；
     * 2) 打开默认类型信息（写入 @class 字段）。这本来是 GenericJackson2JsonRedisSerializer 的默认
     *    行为，自己换 ObjectMapper 后必须手动补上，否则读缓存时拿不回原来的类型，只会得到 Map。
     */
    private static ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return mapper;
    }
}
