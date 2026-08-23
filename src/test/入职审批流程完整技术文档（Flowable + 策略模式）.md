# 入职审批流程完整技术文档（Flowable \+ 策略模式）

## 一、业务概述

本流程为 **员工入职审批流程（onboard\-process）**，用于 HR 发起候选人入职审批，经两级审核通过后，系统自动将「简历数据」转为「正式员工数据」。

流程审核节点：

1. **HR 审核**（系统自动分配 HR）

2. **部门主管确认**（HR 发起时手动选择主管）

两级全部通过 → 自动创建员工记录，入职完成。

任意节点拒绝 → 流程直接结束，入职驳回。

## 二、流程整体结构图（文字版）

## 三、BPMN 节点与变量绑定说明（核心）

### 1\. HR审核节点

节点负责人表达式：`${approver_hr}`

**变量来源：后端策略自动解析（重点）**

由 **OnboardAssigneeStrategy** 自动查询数据库：role=HR 的员工 ID。

### 2\. 部门主管确认节点

节点负责人表达式：`${targetLeaderId}`

**变量来源：前端传入 bizJson 解析**

HR 在发起页面手动选择部门、主管、岗位，存入 bizJson 传给后端。

### 3\. 自动业务节点（服务任务）

服务任务绑定Bean：`${onboardApprovedDelegate}`

流程全部通过后自动执行，读取简历信息，自动生成员工账号。

## 四、所有流程变量来源汇总（最关键、面试必问）

|变量名|作用|来源|
|---|---|---|
|bizId|业务ID = 简历ID|前端传参|
|applyEmpId|流程发起人（HR）|后端当前登录用户|
|approver\_hr|HR审核人ID|**OnboardAssigneeStrategy 自动查表**|
|targetLeaderId|部门主管ID|前端bizJson解析|
|targetDeptName|入职部门|前端bizJson解析|
|targetPosition|入职岗位|前端bizJson解析|
|approved|审批结果 true/false|用户审批点击时传入|

## 五、核心 Bean 职责说明（你最搞不懂的部分）

### 1\. OnboardAssigneeStrategy（审批人解析策略Bean）

**作用：流程启动前，自动算出【HR审批人】**

只负责生成 `approver_hr`，不处理主管审批人。

核心逻辑：查询 t\_employee 表中 role=HR 的用户。

属于 **策略模式** 的其中一个策略实现。

### 2\. AssigneeResolverImpl（策略调度中心）

**不会查数据库、不做业务，只做路由分发**

启动时自动收集所有流程策略Bean，生成 Map：

`key=流程key、value=对应策略Bean`

发起流程时根据 processKey 找到对应的策略，调用其 resolve 方法。

### 3\. OnboardApprovedDelegate（自动创建员工Bean）

实现 JavaDelegate，由 Flowable 自动调用。

流程走到最后服务任务节点自动执行：

读取简历信息 \+ 部门岗位信息 → 插入员工表。

## 六、流程发起完整执行链路（一步不差）

### 步骤1：前端发起流程

传入：processKey、bizId\(简历ID\)、bizJson\(主管/部门/岗位信息\)

### 步骤2：后端准备流程变量

1\. 初始化空 Map variables

2\. 放入基础变量 bizId、applyEmpId

3\. **调用 assigneeResolver\.resolve\(\) 解析审批人**

　→ 匹配到 onboard\-process

　→ 执行 OnboardAssigneeStrategy

　→ 查询HR，返回 \{approver\_hr:xxx\}

4\. putAll 将HR审批人变量并入流程变量

5\. 解析 bizJson，并入 targetLeaderId 等变量

### 步骤3：Flowable启动流程

引擎读取全部变量，开始流转：

第一个节点自动分配给 $\{approver\_hr\}

### 步骤4：HR审批

通过 → 流转到主管节点（自动分配给 $\{targetLeaderId\}）

拒绝 → 流程结束、驳回

### 步骤5：部门主管审批

通过 → 进入服务任务节点

拒绝 → 流程结束、驳回

### 步骤6：系统自动创建员工

执行 OnboardApprovedDelegate\.execute\(\)

读取简历信息 \+ 流程变量部门岗位 → 生成员工账号

### 步骤7：流程结束，更新业务状态

流程状态变为【已通过】

## 七、为什么主管审批人不写在策略里？（重点答疑）

因为：**每一次入职的主管、部门、岗位都是HR手动选择的，不是固定规则能查出来的。**

所以：

- HR审批人：**固定角色** → 后端自动解析（策略类）

- 主管审批人：**动态选人** → 前端传bizJson

## 八、putAll 作用最终解释

策略类返回的是 **一个Map**（未来可扩展多个审批人）

所以必须用 `putAll` 批量导入所有自动解析的审批人变量。

## 九、整体架构优势

1. **完全解耦**：新增流程只需要新增策略类，不用改核心代码

2. **动态审批人**：不用写死ID，人员变动无需改BPMN

3. **安全**：核心审批人由后端查询，前端不可篡改

4. **自动化**：终审通过自动建员工，无需人工操作

> （注：部分内容可能由 AI 生成）
