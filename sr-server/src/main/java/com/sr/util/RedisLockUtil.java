package com.sr.util;

import com.sr.common.exception.BizException;
import com.sr.common.result.ResultCode;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

@Component
public class RedisLockUtil {

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "  return redis.call('del', KEYS[1]) " +
                    "else return 0 end";

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /** 尝试加锁一次，不等待 */
    public String tryLock(String key, long expireSeconds) {
        String value = UUID.randomUUID().toString();
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(expireSeconds));
        return Boolean.TRUE.equals(ok) ? value : null;
    }

    /** 自旋等待加锁；拿到返回 value，等不到就抛异常 */
    public String lock(String key, long expireSeconds, long waitMillis) {
        long deadline = System.currentTimeMillis() + waitMillis;
        while (System.currentTimeMillis() < deadline) {
            String value = tryLock(key, expireSeconds);
            if (value != null) {
                return value;
            }
            LockSupport.parkNanos(50_000_000L);   // 等 50ms 再看一眼
        }
        throw new BizException(ResultCode.CONCURRENT_CONFLICT);
    }

    /** 释放：只删自己那把 */
    public void unlock(String key, String value) {
        redisTemplate.execute(
                new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                List.of(key), value);
    }
}
