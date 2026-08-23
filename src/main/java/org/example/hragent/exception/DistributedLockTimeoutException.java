package org.example.hragent.exception;

/**
 * 分布式锁获取超时异常
 */
public class DistributedLockTimeoutException extends RuntimeException {

    public DistributedLockTimeoutException(String message) {
        super(message);
    }
}