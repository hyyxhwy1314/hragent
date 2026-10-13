<script setup lang="ts">
import { ref, computed, h, onMounted, onBeforeUnmount } from 'vue'
import { message } from 'ant-design-vue'
import {
  FileAddOutlined, FolderOpenOutlined, DownloadOutlined, CloudUploadOutlined,
  ReloadOutlined, AppstoreOutlined, DownOutlined
} from '@ant-design/icons-vue'
import BpmnModeler from 'bpmn-js/lib/Modeler'
import {
  BpmnPropertiesPanelModule,
  BpmnPropertiesProviderModule
} from 'bpmn-js-properties-panel'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import '@bpmn-io/properties-panel/dist/assets/properties-panel.css'
import {
  listDefinitions, getDefinitionXml, deployProcess, type ProcessDefinition
} from '@/api/modules/flow'
import { ensureBpmnDiagram } from '@/utils/bpmn'
import { createTranslateModule } from './bpmnModules'
import { FlowablePropertiesProviderModule, flowableModdle } from './flowableProperties'

/**
 * BPMN 可视化流程设计器（面向 HR 业务人员）：
 * 采用 bpmn.io 官方标准布局——左侧原生调色板 + 中间画布 + 右侧官方属性面板，
 * 仅把界面文案中文化，并额外挂载 Flowable 属性 provider 以便配置审批人 / 流转条件。
 * 支持一键载入常用流程模板、导入 / 导出 XML、部署到 Flowable 引擎。
 */
defineOptions({ name: 'FlowDesigner' })

/** 用标准 BPMN 头包装 process 片段 */
function wrapProcess(processXml: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://flowable.org/bpmn">
${processXml}
</definitions>`
}

/** 空白模板：开始 → 审批 → 结束 */
const BLANK_BPMN = wrapProcess(`  <process id="new-process" name="新建流程" isExecutable="true">
    <startEvent id="start" name="发起"/>
    <userTask id="approve" name="部门主管审批" flowable:assignee="\${approver_dept}"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
    <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
  </process>`)

/** 常用流程模板 */
const TEMPLATES: { key: string; label: string; xml: string }[] = [
  {
    key: 'onboard',
    label: '入职流程',
    xml: wrapProcess(`  <process id="custom-onboard" name="入职流程" isExecutable="true">
    <startEvent id="start" name="发起入职"/>
    <userTask id="hr" name="人事审批" flowable:assignee="\${approver_hr}"/>
    <userTask id="leader" name="用人主管审批" flowable:assignee="\${targetLeaderId}"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="hr"/>
    <sequenceFlow id="f2" sourceRef="hr" targetRef="leader"/>
    <sequenceFlow id="f3" sourceRef="leader" targetRef="end"/>
  </process>`)
  },
  {
    key: 'leave',
    label: '请假流程（含条件分支）',
    xml: wrapProcess(`  <process id="custom-leave" name="请假流程" isExecutable="true">
    <startEvent id="start" name="发起请假"/>
    <userTask id="leader" name="直属主管审批" flowable:assignee="\${approver_dept}"/>
    <exclusiveGateway id="gw" name="是否超过5天"/>
    <userTask id="director" name="总监审批" flowable:assignee="\${approver_director}"/>
    <userTask id="hr" name="人事备案" flowable:assignee="\${approver_hr}"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="leader"/>
    <sequenceFlow id="f2" sourceRef="leader" targetRef="gw"/>
    <sequenceFlow id="f3" name="超过5天" sourceRef="gw" targetRef="director">
      <conditionExpression>\${leaveDays > 5}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="f4" name="5天及以内" sourceRef="gw" targetRef="hr">
      <conditionExpression>\${leaveDays &lt;= 5}</conditionExpression>
    </sequenceFlow>
    <sequenceFlow id="f5" sourceRef="director" targetRef="hr"/>
    <sequenceFlow id="f6" sourceRef="hr" targetRef="end"/>
  </process>`)
  },
  {
    key: 'regular',
    label: '转正流程',
    xml: wrapProcess(`  <process id="custom-regular" name="转正流程" isExecutable="true">
    <startEvent id="start" name="发起转正"/>
    <userTask id="leader" name="直属主管审批" flowable:assignee="\${approver_dept}"/>
    <userTask id="hr" name="人事审批" flowable:assignee="\${approver_hr}"/>
    <endEvent id="end" name="结束"/>
    <sequenceFlow id="f1" sourceRef="start" targetRef="leader"/>
    <sequenceFlow id="f2" sourceRef="leader" targetRef="hr"/>
    <sequenceFlow id="f3" sourceRef="hr" targetRef="end"/>
  </process>`)
  }
]

const canvasRef = ref<HTMLElement>()
const fileInputRef = ref<HTMLInputElement>()
const modeler = ref<any>(null)
const definitions = ref<ProcessDefinition[]>([])
const selectedKey = ref<string>()
const deploying = ref(false)
const errorMsg = ref('')

const defOptions = computed(() =>
  definitions.value.map(d => ({ label: `${d.name}（${d.key} · v${d.version}）`, value: d.key }))
)

let resizeObserver: ResizeObserver | null = null
let resizeTimer: number | undefined

/** 让整条流程完整适配到画布中（首次导入后与容器尺寸变化后调用） */
function fitViewport() {
  requestAnimationFrame(() => {
    try {
      modeler.value?.get('canvas').zoom('fit-viewport')
    } catch { /* 忽略 */ }
  })
}

onMounted(async () => {
  modeler.value = new BpmnModeler({
    container: canvasRef.value,
    // 官方属性面板挂载点（标准用法：propertiesPanel.parent 指定容器）
    propertiesPanel: { parent: '#flow-properties' },
    additionalModules: [
      BpmnPropertiesPanelModule,
      BpmnPropertiesProviderModule,
      FlowablePropertiesProviderModule,
      createTranslateModule()
    ],
    // 注册 Flowable 命名空间，保证 flowable:* 属性可读写、可导出
    moddleExtensions: { flowable: flowableModdle }
  })
  await importXml(BLANK_BPMN)
  loadDefinitions()
  // 容器尺寸变化时：先通知 bpmn-js 重算，再防抖重新适配视图，避免流程图跑到画布外
  resizeObserver = new ResizeObserver(() => {
    try { modeler.value?.get('canvas').resized() } catch { /* 忽略 */ }
    window.clearTimeout(resizeTimer)
    resizeTimer = window.setTimeout(fitViewport, 200)
  })
  if (canvasRef.value) resizeObserver.observe(canvasRef.value)
  // 属性面板挂载会改变布局，稍后再适配一次，确保整条流程可见
  window.setTimeout(fitViewport, 350)
})

onBeforeUnmount(() => {
  window.clearTimeout(resizeTimer)
  resizeObserver?.disconnect()
  resizeObserver = null
  modeler.value?.destroy()
  modeler.value = null
})

// ============ 画布操作 ============
async function importXml(xml: string) {
  try {
    const prepared = await ensureBpmnDiagram(xml)
    await modeler.value.importXML(prepared)
    fitViewport()
    errorMsg.value = ''
  } catch (e: any) {
    console.error('BPMN 导入失败', e)
    errorMsg.value = e?.message || 'BPMN 解析失败，请检查流程内容'
    message.error('流程图加载失败：' + (e?.message || '格式错误'))
  }
}

async function loadDefinitions() {
  try {
    definitions.value = await listDefinitions() || []
  } catch {
    definitions.value = []
  }
}

function onNew() {
  importXml(BLANK_BPMN)
  message.success('已新建空白流程')
}

function onUseTemplate(key: string | number) {
  const tpl = TEMPLATES.find(t => t.key === key)
  if (!tpl) return
  importXml(tpl.xml)
  message.success(`已载入「${tpl.label}」模板`)
}

function triggerImport() {
  fileInputRef.value?.click()
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  await importXml(await file.text())
  message.success('导入成功')
}

async function onLoadDefinition(key: string) {
  if (!key) return
  try {
    const detail = await getDefinitionXml(key)
    if (!detail?.bpmnXml) {
      message.warning('该流程无 BPMN 内容')
      return
    }
    await importXml(detail.bpmnXml)
    message.success(`已加载「${detail.name}」v${detail.version}`)
  } catch { /* 请求层已提示 */ }
}

async function saveXml(): Promise<string> {
  const { xml } = await modeler.value.saveXML({ format: true })
  return xml as string
}

/** 从 XML 中提取 process id 作为部署文件名 */
function processKeyOf(xml: string): string {
  const m = /<process[^>]*\sid="([^"]+)"/.exec(xml)
  return m?.[1] || 'custom-process'
}

function downloadXml(xml: string, filename: string) {
  const blob = new Blob([xml], { type: 'application/xml' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

async function onExport() {
  const xml = await saveXml()
  downloadXml(xml, `${processKeyOf(xml)}.bpmn20.xml`)
  message.success('已导出 BPMN XML')
}

async function onDeploy() {
  if (deploying.value) return
  deploying.value = true
  try {
    const xml = await saveXml()
    const key = processKeyOf(xml)
    const file = new File([xml], `${key}.bpmn20.xml`, { type: 'application/xml' })
    await deployProcess(file)
    message.success(`流程「${key}」部署成功`)
    loadDefinitions()
  } catch { /* 请求层已提示 */ } finally {
    deploying.value = false
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">流程设计器</h2>
      <p class="page-subtitle">
        拖拽左侧节点绘制流程，选中节点后在右侧面板配置审批人与流转条件，确认后一键部署
      </p>
    </div>

    <div class="search-card">
      <div class="designer-toolbar">
        <div class="toolbar-group">
          <a-button type="primary" :icon="h(FileAddOutlined)" @click="onNew">新建</a-button>
          <a-dropdown>
            <a-button :icon="h(AppstoreOutlined)">
              示例模板
              <DownOutlined />
            </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item v-for="t in TEMPLATES" :key="t.key" @click="onUseTemplate(t.key)">
                  {{ t.label }}
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a-button :icon="h(FolderOpenOutlined)" @click="triggerImport">导入 XML</a-button>
          <a-button :icon="h(DownloadOutlined)" @click="onExport">导出 XML</a-button>
          <a-button type="primary" :icon="h(CloudUploadOutlined)" :loading="deploying" @click="onDeploy">
            部署流程
          </a-button>
        </div>
        <div class="toolbar-group">
          <span class="toolbar-label">加载已部署：</span>
          <a-select v-model:value="selectedKey" :options="defOptions" style="width: 240px"
            placeholder="选择已部署流程" allow-clear show-search option-filter-prop="label"
            @change="(v: any) => v && onLoadDefinition(v)" />
          <a-button :icon="h(ReloadOutlined)" @click="loadDefinitions">刷新</a-button>
        </div>
      </div>
      <input ref="fileInputRef" type="file" accept=".xml,.bpmn,.bpmn20.xml" class="hidden-input"
        @change="onFileChange" />
    </div>

    <a-alert v-if="errorMsg" type="error" show-icon :message="errorMsg" class="designer-error" />

    <div class="designer-wrap">
      <div class="designer-main table-card">
        <div ref="canvasRef" class="designer-canvas"></div>
      </div>
      <div class="designer-side table-card">
        <!-- 官方 bpmn-js-properties-panel 挂载点 -->
        <div id="flow-properties" class="properties-host"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 标题不换行，避免内容区变窄时标题被压成两行 */
.page-title {
  white-space: nowrap;
}
.designer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.toolbar-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.toolbar-label {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
}
.hidden-input {
  display: none;
}
.designer-error {
  margin-bottom: 12px;
}
.designer-wrap {
  display: flex;
  align-items: stretch;
  gap: 16px;
  height: calc(100vh - 250px);
  min-height: 520px;
}
.designer-main {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  padding: 0 !important;
  overflow: hidden;
}
/* 绝对铺满父容器，保证 bpmn-js 画布始终有明确尺寸，调色板不会溢出到工具栏上 */
.designer-canvas {
  position: absolute;
  inset: 0;
  background: #fff;
}
.designer-side {
  position: relative;
  width: 300px;
  flex: 0 0 300px;
  min-width: 0;
  padding: 0 !important;
  overflow: hidden;
}
.properties-host {
  position: absolute;
  inset: 0;
}
</style>