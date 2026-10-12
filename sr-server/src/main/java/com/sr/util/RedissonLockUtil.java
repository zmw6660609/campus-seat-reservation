package com.sr.util;

import com.sr.common.exception.BizException;
import com.sr.common.result.ResultCode;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 分布式锁封装。
 *
 * <p>★ @Component 不能少。少了它这个类不会被注册成 Bean，
 * 想注入它的地方只能手动 new —— 而 new 出来的对象里 redissonClient 是 null，
 * 调用时直接 NPE。这类问题不会在编译期暴露。
 */
@Component
public class RedissonLockUtil {
    @Resource
    private RedissonClient redissonClient;
    public <T> T executeWithLock(String key, long waitSeconds, Supplier<T> action){
        RLock lock = redissonClient.getLock(key);
        boolean locked = false;
        try {
            // ⚠️ 只传等待时间，**不要传leaseTime！看门狗才会生效**
            locked = lock.tryLock(waitSeconds, TimeUnit.SECONDS);
            if (!locked) {
                throw new BizException(ResultCode.CONCURRENT_CONFLICT);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 恢复中断标记
            throw new BizException(ResultCode.CONCURRENT_CONFLICT);
        } finally {
            // ⚠️ 只能释放自己线程持有的锁，防止误删别人锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}


