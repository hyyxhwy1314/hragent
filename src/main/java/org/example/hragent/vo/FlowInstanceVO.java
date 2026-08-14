package org.example.hragent.vo;

import lombok.Data;

@Data
public class FlowInstanceVO {
    private Long id;
    private String flowType;
    private Long bizId;
    private Long applyEmpId;
    private String applyEmpName;
    private String flowableProcInstId;
    private String bizJson;
}