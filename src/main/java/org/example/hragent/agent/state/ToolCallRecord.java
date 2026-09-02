package org.example.hragent.agent.state;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.io.Serializable;

/**
 * 可序列化的工具调用请求。
 * <p>
 * LangGraph4j 在节点间克隆状态依赖 Java 原生序列化，而 LangChain4j 的
 * {@link ToolExecutionRequest} 未实现 {@link Serializable}，因此不能直接存入状态通道。
 * 这里仅以它的核心字段（id/name/arguments）构建本 DTO 存入状态，执行时再还原。
 */
public record ToolCallRecord(String id, String name, String arguments) implements Serializable {

    /**
     * 由 LangChain4j 的工具调用请求转换而来
     */
    public static ToolCallRecord from(ToolExecutionRequest request) {
        return new ToolCallRecord(request.id(), request.name(), request.arguments());
    }

    /**
     * 还原为 LangChain4j 的工具调用请求
     */
    public ToolExecutionRequest toRequest() {
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments(arguments)
                .build();
    }
}