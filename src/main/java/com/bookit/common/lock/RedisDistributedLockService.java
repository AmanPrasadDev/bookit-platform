package com.bookit.common.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redis-backed implementation of DistributedLockService using atomic setIfAbsent (SET NX PX)
 * and atomic Lua script release.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDistributedLockService implements DistributedLockService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Canonical Redis unlock Lua script:
     * Only deletes the key if its current value in Redis matches the caller's lockValue token.
     * Prevents Thread A from accidentally deleting Thread B's lock if Thread A's TTL expired.
     */
    private static final String UNLOCK_LUA_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(UNLOCK_LUA_SCRIPT, Long.class);

    @Override
    public boolean acquireLock(String key, String lockValue, Duration ttl) {
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, lockValue, ttl);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean acquireAllLocks(List<String> keys, String lockValue, Duration ttl) {
        if (CollectionUtils.isEmpty(keys)) {
            return true;
        }

        List<String> acquiredKeys = new ArrayList<>();
        for (String key : keys) {
            boolean acquired = acquireLock(key, lockValue, ttl);
            if (!acquired) {
                log.warn("Failed to acquire distributed lock for key '{}'. Rolling back {} previously acquired locks.",
                        key, acquiredKeys.size());
                releaseAllLocks(acquiredKeys, lockValue);
                return false;
            }
            acquiredKeys.add(key);
        }
        return true;
    }

    @Override
    public void releaseLock(String key, String lockValue) {
        if (key == null || lockValue == null) {
            return;
        }
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(key),
                lockValue
        );
    }

    @Override
    public void releaseAllLocks(List<String> keys, String lockValue) {
        if (CollectionUtils.isEmpty(keys) || lockValue == null) {
            return;
        }
        for (String key : keys) {
            releaseLock(key, lockValue);
        }
    }
}
