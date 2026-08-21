package org.example.hragent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程轨迹节点
 */
@Data
public class FlowTraceVO {

    /** 节点名称 */
    private String nodeName;

    /** 处理人姓名 */
    private String assigneeName;

    /** 节点状态：待处理 / 已通过 / 已拒绝 / 已处理 */
    private String status;

    /** 审批结果：true=通过 false=拒绝 null=未处理 */
    private Boolean approved;

    /** 审批意见 */
    private String comment;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    /** 处理时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
}
