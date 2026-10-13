/**
 * Flowable 属性面板 Provider。
 *
 * 官方 `bpmn-js-properties-panel` 只自带 Camunda / Zeebe 的属性 provider（写入 `camunda:` 命名空间），
 * 本项目流程引擎是 Flowable（命名空间 `http://flowable.org/bpmn`，属性形如 `flowable:assignee`）。
 * 因此这里注册一个轻量 provider，在官方属性面板中追加一个「流程节点配置」分组，
 * 用于编辑流程引擎真正识别的 flowable 属性：
 *   - 审批节点（UserTask）：审批人 / 候选用户 / 候选组
 *   - 自动任务（ServiceTask）：委托表达式 / 异步执行
 *   - 连线（SequenceFlow）：流转条件
 */
import { is, getBusinessObject } from 'bpmn-js/lib/util/ModelUtil'
import {
  Group, TextFieldEntry, CheckboxEntry,
  isTextFieldEntryEdited, isCheckboxEntryEdited
} from '@bpmn-io/properties-panel'
import { useService } from 'bpmn-js-properties-panel'
// Flowable 命名空间 moddle 描述符：让 bpmn-js 正确读写 flowable:* 属性（导出的 XML 带 flowable 前缀）
import flowableModdle from 'flowable-bpmn-moddle/resources/camunda.json'

export { flowableModdle }

/**
 * 生成一个「flowable 属性文本框」组件工厂。
 * 在模块加载期调用一次即可得到稳定的组件引用（避免每次选中节点都重新创建组件）。
 * 写入走命令栈（element.updateModdleProperties），因此支持撤销 / 重做。
 */
function makeTextEntry(attr: string, label: string) {
  return function FlowableTextFieldEntry({ element, id }: any) {
    const commandStack = useService('commandStack')
    const debounce = useService('debounceInput')
    const businessObject = getBusinessObject(element)
    return TextFieldEntry({
      element,
      id,
      label,
      debounce,
      getValue: () => businessObject.get(`flowable:${attr}`) || '',
      setValue: (value: string) => {
        commandStack.execute('element.updateModdleProperties', {
          element,
          moddleElement: businessObject,
          properties: { [`flowable:${attr}`]: value === '' ? undefined : value }
        })
      }
    })
  }
}

const AssigneeEntry = makeTextEntry('assignee', '审批人（表达式或用户ID）')
const CandidateUsersEntry = makeTextEntry('candidateUsers', '候选用户（多个用逗号分隔）')
const CandidateGroupsEntry = makeTextEntry('candidateGroups', '候选组（如 HR）')
const DelegateExpressionEntry = makeTextEntry('delegateExpression', '委托表达式（Bean）')

/** 自动任务：异步执行开关 */
function AsyncEntry({ element, id }: any) {
  const commandStack = useService('commandStack')
  const businessObject = getBusinessObject(element)
  return CheckboxEntry({
    element,
    id,
    label: '异步执行',
    getValue: () => businessObject.get('flowable:async') === true,
    setValue: (value: boolean) => {
      commandStack.execute('element.updateModdleProperties', {
        element,
        moddleElement: businessObject,
        properties: { 'flowable:async': value ? true : undefined }
      })
    }
  })
}

/** 连线：流转条件（标准 BPMN conditionExpression） */
function ConditionEntry({ element, id }: any) {
  const modeling = useService('modeling')
  const moddle = useService('moddle')
  const debounce = useService('debounceInput')
  const businessObject = getBusinessObject(element)
  return TextFieldEntry({
    element,
    id,
    label: '流转条件',
    debounce,
    getValue: () => {
      const expression = businessObject.get('conditionExpression')
      return (expression && expression.body) || ''
    },
    setValue: (value: string) => {
      modeling.updateProperties(element, {
        conditionExpression: value
          ? moddle.create('bpmn:FormalExpression', { body: value })
          : undefined
      })
    }
  })
}

/** 根据选中节点类型组装分组；不适用时返回 null（不显示该分组） */
function createFlowableGroup(element: any) {
  const entries: any[] = []

  if (is(element, 'bpmn:UserTask')) {
    entries.push({ id: 'flowable-assignee', component: AssigneeEntry, isEdited: isTextFieldEntryEdited })
    entries.push({ id: 'flowable-candidateUsers', component: CandidateUsersEntry, isEdited: isTextFieldEntryEdited })
    entries.push({ id: 'flowable-candidateGroups', component: CandidateGroupsEntry, isEdited: isTextFieldEntryEdited })
  }

  if (is(element, 'bpmn:ServiceTask')) {
    entries.push({ id: 'flowable-delegateExpression', component: DelegateExpressionEntry, isEdited: isTextFieldEntryEdited })
    entries.push({ id: 'flowable-async', component: AsyncEntry, isEdited: isCheckboxEntryEdited })
  }

  if (is(element, 'bpmn:SequenceFlow')) {
    entries.push({ id: 'flowable-condition', component: ConditionEntry, isEdited: isTextFieldEntryEdited })
  }

  if (!entries.length) {
    return null
  }

  return {
    id: 'flowable',
    label: '流程节点配置',
    entries,
    component: Group
  }
}

/** 把「流程节点配置」分组追加到官方属性面板 */
class FlowablePropertiesProvider {
  constructor(propertiesPanel: any) {
    // 注意：该版本签名为 registerProvider(priority, provider)
    // 优先级高于官方内置 provider，保证分组显示在合适位置
    propertiesPanel.registerProvider(500, this)
  }

  getGroups(element: any) {
    return (groups: any[]) => {
      const group = createFlowableGroup(element)
      if (group) {
        groups.push(group)
      }
      return groups
    }
  }
}
FlowablePropertiesProvider.$inject = ['propertiesPanel']

/** 可挂载到 bpmn-js additionalModules 的模块定义 */
export const FlowablePropertiesProviderModule = {
  __init__: ['flowablePropertiesProvider'],
  flowablePropertiesProvider: ['type', FlowablePropertiesProvider]
}