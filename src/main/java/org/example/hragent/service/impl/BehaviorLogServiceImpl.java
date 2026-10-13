package org.example.hragent.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.hragent.entity.system.BehaviorLog;
import org.example.hragent.mapper.system.BehaviorLogMapper;
import org.example.hragent.service.BehaviorLogService;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用户行为日志 Service 实现
 * <p>
 * 以独立的单线程异步队列落库，避免高频行为日志阻塞业务请求线程。
 * 采用有界队列 + 丢弃策略，极端高并发下优先保证业务可用性。
 */
@Slf4j
@Service
public class BehaviorLogServiceImpl implements BehaviorLogService {

    private final BehaviorLogMapper behaviorLogMapper;

    /** 单线程异步落库，保证写入有序且不阻塞业务线程 */
    private final ExecutorService logExecutor;
    private final BlockingQueue<Runnable> queue;

    public BehaviorLogServiceImpl(BehaviorLogMapper behaviorLogMapper) {
        this.behaviorLogMapper = behaviorLogMapper;
        this.queue = new LinkedBlockingQueue<>(2000);
        AtomicInteger counter = new AtomicInteger(0);
        this.logExecutor = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                queue,
                r -> {
                    Thread t = new Thread(r, "behavior-log-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void record(BehaviorLog behaviorLog) {
        try {
            logExecutor.execute(() -> {
                try {
                    behaviorLogMapper.insert(behaviorLog);
                } catch (Exception e) {
                    log.warn("行为日志落库失败 action={} err={}", behaviorLog.getAction(), e.getMessage());
                }
            });
        } catch (Exception e) {
            // 队列已满或其他异常：直接丢弃，不阻塞业务线程
            log.warn("行为日志提交队列已满，丢弃记录 action={}", behaviorLog.getAction());
        }
    }
}