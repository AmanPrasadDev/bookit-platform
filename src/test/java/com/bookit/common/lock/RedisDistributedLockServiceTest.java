package com.bookit.common.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisDistributedLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new RedisDistributedLockService(stringRedisTemplate);
    }

    // =========================================================================
    // 1. acquireLock Tests
    // =========================================================================

    @Nested
    @DisplayName("acquireLock")
    class AcquireLockTests {

        @Test
        @DisplayName("Should return true when Redis setIfAbsent succeeds")
        void acquireLock_whenSuccessful_shouldReturnTrue() {
            String key = "lock:seat:1";
            String lockValue = "hold-token-123";
            Duration ttl = Duration.ofMinutes(10);

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(key, lockValue, ttl)).thenReturn(Boolean.TRUE);

            boolean acquired = lockService.acquireLock(key, lockValue, ttl);

            assertThat(acquired).isTrue();
            verify(valueOperations).setIfAbsent(key, lockValue, ttl);
        }

        @Test
        @DisplayName("Should return false when Redis setIfAbsent returns false (key already held)")
        void acquireLock_whenKeyAlreadyHeld_shouldReturnFalse() {
            String key = "lock:seat:1";
            String lockValue = "hold-token-123";
            Duration ttl = Duration.ofMinutes(10);

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(key, lockValue, ttl)).thenReturn(Boolean.FALSE);

            boolean acquired = lockService.acquireLock(key, lockValue, ttl);

            assertThat(acquired).isFalse();
            verify(valueOperations).setIfAbsent(key, lockValue, ttl);
        }

        @Test
        @DisplayName("Should return false when Redis setIfAbsent returns null (e.g. timeout)")
        void acquireLock_whenRedisReturnsNull_shouldReturnFalse() {
            String key = "lock:seat:1";
            String lockValue = "hold-token-123";
            Duration ttl = Duration.ofMinutes(10);

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(key, lockValue, ttl)).thenReturn(null);

            boolean acquired = lockService.acquireLock(key, lockValue, ttl);

            assertThat(acquired).isFalse();
            verify(valueOperations).setIfAbsent(key, lockValue, ttl);
        }
    }

    // =========================================================================
    // 2. acquireAllLocks Tests
    // =========================================================================

    @Nested
    @DisplayName("acquireAllLocks")
    class AcquireAllLocksTests {

        @Test
        @DisplayName("Should return true when all keys are successfully locked")
        void acquireAllLocks_whenAllSucceed_shouldReturnTrue() {
            List<String> keys = List.of("lock:seat:1", "lock:seat:2", "lock:seat:3");
            String lockValue = "hold-token-123";
            Duration ttl = Duration.ofMinutes(10);

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq(lockValue), eq(ttl))).thenReturn(Boolean.TRUE);

            boolean acquired = lockService.acquireAllLocks(keys, lockValue, ttl);

            assertThat(acquired).isTrue();
            verify(valueOperations, times(3)).setIfAbsent(anyString(), eq(lockValue), eq(ttl));
            verify(stringRedisTemplate, never()).execute(any(), anyList(), any());
        }

        @Test
        @DisplayName("Should rollback previously acquired keys and return false if any key fails")
        void acquireAllLocks_whenOneKeyFails_shouldRollbackAndReturnFalse() {
            String key1 = "lock:seat:1";
            String key2 = "lock:seat:2";
            String key3 = "lock:seat:3";
            List<String> keys = List.of(key1, key2, key3);
            String lockValue = "hold-token-123";
            Duration ttl = Duration.ofMinutes(10);

            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            // key1 and key2 succeed, key3 fails (already locked)
            when(valueOperations.setIfAbsent(key1, lockValue, ttl)).thenReturn(Boolean.TRUE);
            when(valueOperations.setIfAbsent(key2, lockValue, ttl)).thenReturn(Boolean.TRUE);
            when(valueOperations.setIfAbsent(key3, lockValue, ttl)).thenReturn(Boolean.FALSE);

            boolean acquired = lockService.acquireAllLocks(keys, lockValue, ttl);

            assertThat(acquired).isFalse();
            // Verify rollback: only key1 and key2 were acquired, so only key1 and key2 must be released!
            verify(stringRedisTemplate).execute(any(), eq(Collections.singletonList(key1)), eq(lockValue));
            verify(stringRedisTemplate).execute(any(), eq(Collections.singletonList(key2)), eq(lockValue));
            // key3 was never acquired, so it must NOT be released
            verify(stringRedisTemplate, never()).execute(any(), eq(Collections.singletonList(key3)), eq(lockValue));
        }

        @Test
        @DisplayName("Should return true immediately when keys list is empty")
        void acquireAllLocks_whenEmptyList_shouldReturnTrue() {
            boolean acquired = lockService.acquireAllLocks(List.of(), "hold-token-123", Duration.ofMinutes(10));

            assertThat(acquired).isTrue();
            verifyNoInteractions(stringRedisTemplate);
        }

        @Test
        @DisplayName("Should return true immediately when keys list is null")
        void acquireAllLocks_whenNullList_shouldReturnTrue() {
            boolean acquired = lockService.acquireAllLocks(null, "hold-token-123", Duration.ofMinutes(10));

            assertThat(acquired).isTrue();
            verifyNoInteractions(stringRedisTemplate);
        }
    }

    // =========================================================================
    // 3. releaseLock & releaseAllLocks Tests
    // =========================================================================

    @Nested
    @DisplayName("releaseLock & releaseAllLocks")
    class ReleaseLockTests {

        @Test
        @DisplayName("releaseLock should execute atomic unlock Lua script")
        void releaseLock_shouldExecuteLuaScript() {
            String key = "lock:seat:1";
            String lockValue = "hold-token-123";

            lockService.releaseLock(key, lockValue);

            verify(stringRedisTemplate).execute(
                    any(),
                    eq(Collections.singletonList(key)),
                    eq(lockValue)
            );
        }

        @Test
        @DisplayName("releaseLock should ignore null key or null lockValue")
        void releaseLock_whenNullInputs_shouldDoNothing() {
            lockService.releaseLock(null, "token");
            lockService.releaseLock("lock:1", null);

            verifyNoInteractions(stringRedisTemplate);
        }

        @Test
        @DisplayName("releaseAllLocks should release each key in the list")
        void releaseAllLocks_shouldReleaseEachKey() {
            List<String> keys = List.of("lock:seat:1", "lock:seat:2");
            String lockValue = "hold-token-123";

            lockService.releaseAllLocks(keys, lockValue);

            verify(stringRedisTemplate).execute(any(), eq(Collections.singletonList("lock:seat:1")), eq(lockValue));
            verify(stringRedisTemplate).execute(any(), eq(Collections.singletonList("lock:seat:2")), eq(lockValue));
        }

        @Test
        @DisplayName("releaseAllLocks should ignore empty or null keys list")
        void releaseAllLocks_whenEmptyOrNull_shouldDoNothing() {
            lockService.releaseAllLocks(List.of(), "token");
            lockService.releaseAllLocks(null, "token");
            lockService.releaseAllLocks(List.of("lock:1"), null);

            verifyNoInteractions(stringRedisTemplate);
        }
    }
}
