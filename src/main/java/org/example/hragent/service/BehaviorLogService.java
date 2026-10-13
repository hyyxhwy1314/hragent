package org.example.hragent.service;

import org.example.hragent.entity.system.BehaviorLog;

/**
 * 用户行为日志 Service
 * <p>
 * 负责行为日志的异步落库（不阻塞业务请求），以及供工作台
 * Token 消耗看板与操作日志时间线查询。
 */
public interface BehaviorLogService {

    /**
     * 异步记录一条行为日志
     *
     * @param log 行为日志对象（未持久化）
     */
    void record(BehaviorLog log);
}