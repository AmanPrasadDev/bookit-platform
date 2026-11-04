package com.bookit.common.lock;

import java.time.Duration;
import java.util.List;

/**
 * Contract for acquiring and releasing distributed locks across a cluster.
 */
public interface DistributedLockService {

    /**
     * Attempts to acquire an exclusive lock on a single resource key.
     *
     * @param key       the unique lock key (e.g. "lock:seat:uuid")
     * @param lockValue unique token identifying the lock owner (e.g. holdId or customerId)
     * @param ttl       lock expiration time-to-live to prevent permanent deadlocks if a node crashes
     * @return true if the lock was acquired, false if already held by another owner
     */
    boolean acquireLock(String key, String lockValue, Duration ttl);

    /**
     * Atomically attempts to acquire locks on all specified resource keys.
     * If ANY key fails to be acquired, all previously acquired keys in this batch MUST be released immediately.
     *
     * @param keys      list of unique lock keys to acquire
     * @param lockValue unique token identifying the lock owner
     * @param ttl       expiration time-to-live for all acquired locks
     * @return true if ALL locks were successfully acquired; false if any failed (with full rollback)
     */
    boolean acquireAllLocks(List<String> keys, String lockValue, Duration ttl);

    /**
     * Releases a previously acquired lock, ONLY if the current value in storage matches the owner's lockValue.
     *
     * @param key       the unique lock key to release
     * @param lockValue the token that was used when acquiring the lock
     */
    void releaseLock(String key, String lockValue);

    /**
     * Releases a collection of previously acquired locks for the specified owner.
     *
     * @param keys      list of unique lock keys to release
     * @param lockValue the token that was used when acquiring the locks
     */
    void releaseAllLocks(List<String> keys, String lockValue);
}
