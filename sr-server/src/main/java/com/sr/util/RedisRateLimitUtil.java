package com.sr.util;

import com.sr.constant.RedisKeyConstant;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
@Component
public class RedisRateLimitUtil {
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TOKEN_BUCKET_LUA= """
        local key = KEYS[1]
        local capacity = tonumber(ARGV[1])
        local rate = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])
        local need = tonumber(ARGV[4])

        local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')
        local tokens = tonumber(bucket[1])
        local lastRefill = tonumber(bucket[2])

        if tokens == nil then
            tokens = capacity
            lastRefill = now
        end

        local delta = math.max(0, now - lastRefill)
        tokens = math.min(capacity, tokens + delta * rate / 1000)
        lastRefill = now

        local allowed = 0
        if tokens >= need then
            tokens = tokens - need
            allowed = 1
        end

        redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', lastRefill)
        redis.call('PEXPIRE', key, math.ceil(capacity / rate * 1000) * 2)

        return allowed
        """;
    public boolean tryAcquireReserveLimit(Long userId, long capacity, long rate) {
        String limitKey = String.format(RedisKeyConstant.RATE_LIMIT_RESERVE_USER, userId);
        // 当前毫秒时间，每次请求消耗1个令牌
        List<String> keys = List.of(limitKey);
        List<Object> args = List.of(capacity, rate, System.currentTimeMillis(), 1);
        Object result = redisTemplate.execute(new DefaultRedisScript<>(TOKEN_BUCKET_LUA, Long.class), keys, args.toArray());
        return Long.valueOf(1).equals(result);
    }
}
