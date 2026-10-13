package org.example.hragent.agent.tools;

/**
 * HR 工具集标记接口。
 * <p>
 * 实现该接口的 Spring Bean 会被 {@code ToolRegistry} 自动收集为本地 {@code @Tool} 工具，
 * 新增业务域工具只需实现本接口并声明 {@code @Component}，无需改动注册表代码。
 */
public interface HrToolSet {
}