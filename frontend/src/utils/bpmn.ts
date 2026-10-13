import { layoutProcess } from 'bpmn-auto-layout'

/**
 * 自动布局生成的图形整体向右偏移量。
 * bpmn-js 的调色板悬浮在画布左上角（约 x: 20~114），若流程从 x≈25 开始会被调色板遮挡，
 * 因此统一右移一段距离，保证起始节点可见。
 */
const LAYOUT_OFFSET_X = 160

/** 递归遍历 XML 元素（按 localName 匹配，避免命名空间前缀差异） */
function* walkElements(node: Element): Generator<Element> {
  yield node
  for (let i = 0; i < node.children.length; i++) {
    yield* walkElements(node.children[i])
  }
}

function toNumber(value: string | null): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

function findBounds(el: Element): Element | undefined {
  return Array.from(el.children).find(child => child.localName === 'Bounds')
}

/**
 * 把 bpmn-auto-layout 生成的「纵向」布局转置为「横向」布局。
 * <p>
 * bpmn-auto-layout 默认产出从上到下的纵向布局（相对于 BPMN 从左到右的惯例），
 * 且起始节点贴左，会被调色板遮挡。这里交换所有图形坐标的 x/y（形状同时交换宽高），
 * 得到从左到右的常规流程图，再整体右移避开调色板。
 */
function transposeToHorizontal(xml: string): string {
  const doc = new DOMParser().parseFromString(xml, 'application/xml')
  if (doc.getElementsByTagName('parsererror').length > 0) {
    return xml
  }

  for (const el of walkElements(doc.documentElement)) {
    if (el.localName === 'BPMNShape') {
      const bounds = findBounds(el)
      if (!bounds) continue
      const x = toNumber(bounds.getAttribute('x'))
      const y = toNumber(bounds.getAttribute('y'))
      const width = toNumber(bounds.getAttribute('width'))
      const height = toNumber(bounds.getAttribute('height'))
      bounds.setAttribute('x', String(y + LAYOUT_OFFSET_X))
      bounds.setAttribute('y', String(x))
      bounds.setAttribute('width', String(height))
      bounds.setAttribute('height', String(width))
    } else if (el.localName === 'waypoint') {
      const x = toNumber(el.getAttribute('x'))
      const y = toNumber(el.getAttribute('y'))
      el.setAttribute('x', String(y + LAYOUT_OFFSET_X))
      el.setAttribute('y', String(x))
    } else if (el.localName === 'BPMNLabel') {
      const bounds = findBounds(el)
      if (!bounds) continue
      const x = toNumber(bounds.getAttribute('x'))
      const y = toNumber(bounds.getAttribute('y'))
      bounds.setAttribute('x', String(y + LAYOUT_OFFSET_X))
      bounds.setAttribute('y', String(x))
    }
  }

  return new XMLSerializer().serializeToString(doc)
}

/**
 * 确保 BPMN XML 含图形信息（DI）。
 * <p>
 * bpmn-js 渲染依赖 `bpmndi:BPMNDiagram` 图形信息，缺失时会报 “no diagram to display”。
 * 项目中的流程定义（含手动编写的 .bpmn20.xml 与流程设计器新建模板）通常只含语义节点、
 * 不含 DI，因此导入前统一用 bpmn-auto-layout 自动补齐布局，并转置为横向布局；
 * 已含 DI 的 XML 原样返回。
 *
 * @param xml BPMN XML 文本
 * @returns 补全 DI 后的 BPMN XML；补全失败时回退原始 XML
 */
export async function ensureBpmnDiagram(xml: string): Promise<string> {
  if (!xml || /<bpmndi:BPMNDiagram[\s>]/i.test(xml)) {
    return xml
  }
  try {
    const laidOut = await layoutProcess(xml)
    return transposeToHorizontal(laidOut)
  } catch (e) {
    console.error('BPMN 自动布局失败，回退原始 XML', e)
    return xml
  }
}