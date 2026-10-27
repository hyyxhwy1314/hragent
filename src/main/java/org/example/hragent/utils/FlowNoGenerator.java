package org.example.hragent.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 业务流程流水号生成
 */
public final class FlowNoGenerator {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private FlowNoGenerator() {}

    /** 生成业务流水号 FLyyyyMMddHHmmss + 4 位自增（简化实现） */
    public static String generate() {
        return "FL" + LocalDateTime.now().format(TS) + String.format("%04d", System.currentTimeMillis() % 10000);
    }
}
