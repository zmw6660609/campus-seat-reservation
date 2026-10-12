package com.sr.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置。
 *
 * <p>★ @Configuration 不能少。少了它，类里的 @Bean 方法根本不会被处理，
 * RedissonClient 这个 Bean 压根不存在 —— 注入它的地方会启动失败，
 * 而如果是手动 new 出来的工具类，就变成运行时 NPE（更难查）。
 */
@Configuration
public class RedissonConfig {
    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + host + ":" + port);
        return Redisson.create(config);
    }
}
