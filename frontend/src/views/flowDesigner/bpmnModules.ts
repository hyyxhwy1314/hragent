/**
 * bpmn-js 界面中文化。
 *
 * 使用 bpmn-js 原生调色板与右键菜单，仅把内置英文文案翻译为中文，
 * 让 HR 业务人员「看得懂」，同时保留完整的节点类型（并行网关、子流程等）。
 */

/** 英文模板 → 中文（覆盖原生调色板 Palette 与右键 ContextPad / Popup 菜单） */
const ZH_DICT: Record<string, string> = {
  // 画布工具
  'Activate hand tool': '拖动画布',
  'Activate lasso tool': '框选',
  'Activate create/remove space tool': '调整间距',
  'Activate global connect tool': '连线',
  // 调色板（创建节点）
  'Create start event': '开始事件',
  'Create end event': '结束事件',
  'Create gateway': '网关（条件/并行）',
  'Create task': '任务（审批/自动）',
  'Create intermediate/boundary event': '中间/边界事件',
  'Create expanded sub-process': '展开子流程',
  'Create data object reference': '数据对象',
  'Create data store reference': '数据存储',
  'Create group': '分组',
  'Create pool/participant': '泳池/参与者',
  // 右键菜单（ContextPad：追加节点）
  'Delete': '删除',
  'Change element': '更换节点类型',
  'Add text annotation': '添加注释',
  'Connect to other element': '连接到其他节点',
  'Connect using association': '用关联线连接',
  'Connect using data input association': '用数据输入关联连接',
  'Append task': '追加任务',
  'Append end event': '追加结束事件',
  'Append gateway': '追加网关',
  'Append intermediate/boundary event': '追加中间/边界事件',
  'Append compensation activity': '追加补偿活动',
  'Append receive task': '追加接收任务',
  'Append message intermediate catch event': '追加消息事件',
  'Append timer intermediate catch event': '追加定时事件',
  'Append conditional intermediate catch event': '追加条件事件',
  'Append signal intermediate catch event': '追加信号事件',
  'Add lane above': '在上方添加泳道',
  'Add lane below': '在下方添加泳道',
  'Divide into two lanes': '拆分为两条泳道',
  'Divide into three lanes': '拆分为三条泳道',
  // 更换类型 Popup 菜单
  'Task': '任务',
  'User task': '审批节点',
  'Service task': '自动任务',
  'Send task': '发送任务',
  'Receive task': '接收任务',
  'Manual task': '手动任务',
  'Business rule task': '规则任务',
  'Script task': '脚本任务',
  'Call activity': '调用活动',
  'Sub process (collapsed)': '折叠子流程',
  'Sub process (expanded)': '展开子流程',
  'Start event': '开始事件',
  'End event': '结束事件',
  'Intermediate throw event': '中间抛出事件',
  'Intermediate catch event': '中间捕获事件',
  'Boundary event': '边界事件',
  'Exclusive gateway': '排他网关（条件分支）',
  'Inclusive gateway': '包容网关',
  'Parallel gateway': '并行网关（会签）',
  'Event based gateway': '事件网关',
  'Complex gateway': '复杂网关',
  'Data object reference': '数据对象',
  'Data store reference': '数据存储',
  'Group': '分组',
  'Text annotation': '文本注释',
  'Pool/Participant': '泳池/参与者',
  'Expanded pool/participant': '展开泳池/参与者',
  'Collapsed pool': '折叠泳池'
}

/**
 * 官方属性面板（bpmn-js-properties-panel 的 BpmnPropertiesProviderModule）文案。
 * 面板同样通过 diagram-js 的 translate 服务取文案，因此只需在此补充词条即可中文化。
 */
const ZH_PROPERTIES_DICT: Record<string, string> = {
  // 空状态
  'Select an element to edit its properties.': '请选中一个节点，即可在此编辑其属性',
  'Multiple elements are selected. Select a single element to edit its properties.':
    '已选中多个节点，请只选择一个节点来编辑属性',
  // 分组标题
  'General': '常规',
  'Documentation': '节点说明',
  'Compensation': '补偿',
  'Error': '错误',
  'Message': '消息',
  'Signal': '信号',
  'Link': '链接',
  'Escalation': '升级',
  'Timer': '定时',
  'Multi-instance': '多实例',
  'Completion': '完成条件',
  // 字段
  'ID': '标识 ID',
  'Participant ID': '参与者 ID',
  'Name': '名称',
  'Code': '编码',
  'Executable': '可执行',
  'Process name': '流程名称',
  'Process ID': '流程 ID',
  'Version tag': '版本标签',
  'Process variables': '流程变量',
  'Element documentation': '节点说明',
  'Process documentation': '流程说明',
  'Type': '类型',
  'Value': '值',
  'Date': '日期',
  'Duration': '时长',
  'Cycle': '周期',
  'Priority': '优先级',
  'Condition expression': '条件表达式',
  'Extension properties': '扩展属性',
  'Activity reference': '活动引用',
  'Completion condition': '完成条件',
  'Cancel remaining instances': '取消剩余实例',
  'Wait for completion': '等待完成',
  'Loop cardinality': '循环次数',
  'Global error reference': '全局错误引用',
  'Global escalation reference': '全局升级引用',
  'Global message reference': '全局消息引用',
  'Global signal reference': '全局信号引用',
  'Candidate starter': '流程发起人',
  'Candidate starter groups': '发起人候选组',
  'Candidate starter users': '发起人候选用户',
  '<none>': '（无）',
  'Create new ...': '新建…',
  'Participant Name': '参与者名称',
  'Label': '标签',
  '<empty>': '（空）',
  'Active elements collection': '活动元素集合',
  // 标识 ID 校验提示
  'ID must not be empty.': '标识 ID 不能为空',
  'ID must be unique.': '标识 ID 必须唯一',
  'ID must not contain spaces.': '标识 ID 不能包含空格',
  'ID must not contain prefix.': '标识 ID 不能包含前缀',
  'ID must be a valid QName.': '标识 ID 必须是合法的 QName',
  // 面板顶部元素类型标签（由 header 显示，CSS 会转为大写，故键为首字母大写的类型名）
  'Task': '任务',
  'User Task': '审批节点',
  'Service Task': '自动任务',
  'Send Task': '发送任务',
  'Receive Task': '接收任务',
  'Manual Task': '手动任务',
  'Business Rule Task': '规则任务',
  'Script Task': '脚本任务',
  'Call Activity': '调用活动',
  'Sub Process': '子流程',
  'Start Event': '开始事件',
  'End Event': '结束事件',
  'Intermediate Throw Event': '中间抛出事件',
  'Intermediate Catch Event': '中间捕获事件',
  'Boundary Event': '边界事件',
  'Exclusive Gateway': '排他网关（条件分支）',
  'Inclusive Gateway': '包容网关',
  'Parallel Gateway': '并行网关（会签）',
  'Event Based Gateway': '事件网关',
  'Complex Gateway': '复杂网关',
  'Data Object Reference': '数据对象',
  'Data Store Reference': '数据存储',
  'Text Annotation': '文本注释',
  'Pool': '泳池',
  'Participant': '参与者',
  'Lane': '泳道',
  'Collaboration': '协作',
  'Process': '流程',
  'Sequence Flow': '连线',
  'Association': '关联线'
}

/**
 * 生成翻译模块，覆盖 diagram-js / bpmn-js 内置英文文案。
 * @returns 可挂载到 additionalModules 的模块
 */
export function createTranslateModule() {
  const dict: Record<string, string> = { ...ZH_DICT, ...ZH_PROPERTIES_DICT }
  return {
    translate: ['value', (template: string, replacements?: Record<string, string>) => {
      let text = Object.prototype.hasOwnProperty.call(dict, template)
        ? dict[template]
        : template
      if (replacements) {
        Object.keys(replacements).forEach((key) => {
          text = text.split(`{${key}}`).join(replacements[key])
        })
      }
      return text
    }]
  }
}