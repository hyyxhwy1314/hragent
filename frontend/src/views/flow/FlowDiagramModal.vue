<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { Modal, Spin, Empty } from 'ant-design-vue'
import BpmnViewer from 'bpmn-js/lib/Viewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'
import { getFlowDiagram } from '@/api/modules/flow'
import { ensureBpmnDiagram } from '@/utils/bpmn'

/**
 * 流程运行态图查看器：bpmn-js Viewer 渲染 BPMN，并高亮已完成（绿）/进行中（蓝）节点。
 */
const props = defineProps<{ open: boolean; flowInstanceId?: number; title?: string }>()
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>()

const containerRef = ref<HTMLElement>()
const loading = ref(false)
const hasDiagram = ref(false)
let viewer: any = null

async function render(id: number) {
  loading.value = true
  hasDiagram.value = false
  try {
    const data = await getFlowDiagram(id)
    if (!data?.bpmnXml) {
      return
    }
    await nextTick()
    if (!viewer && containerRef.value) {
      viewer = new BpmnViewer({ container: containerRef.value })
    }
    const xml = await ensureBpmnDiagram(data.bpmnXml)
    await viewer.importXML(xml)
    const canvas = viewer.get('canvas')
    canvas.zoom('fit-viewport')
    ;(data.finishedActivityIds || []).forEach(a => canvas.addMarker(a, 'flow-node-finished'))
    ;(data.activeActivityIds || []).forEach(a => canvas.addMarker(a, 'flow-node-active'))
    hasDiagram.value = true
  } catch (e) {
    console.error('流程图渲染失败', e)
    hasDiagram.value = false
  } finally {
    loading.value = false
  }
}

watch(() => props.open, (v) => {
  if (v && props.flowInstanceId) render(props.flowInstanceId)
})
</script>

<template>
  <Modal :open="open" :title="title || '流程图'" :footer="null" width="900px"
    @cancel="emit('update:open', false)">
    <div class="diagram-body">
      <div v-if="loading" class="diagram-loading"><Spin /></div>
      <div v-show="!loading" ref="containerRef" class="flow-diagram-canvas"></div>
      <div v-if="!loading && !hasDiagram" class="diagram-empty">
        <Empty description="暂无流程图" />
      </div>
      <div class="diagram-legend">
        <span><i class="dot dot-finished"></i>已完成</span>
        <span><i class="dot dot-active"></i>进行中</span>
      </div>
    </div>
  </Modal>
</template>

<style>
/* bpmn-js 高亮标记样式需作用于动态 SVG，故为全局样式（以容器类限定作用域） */
.flow-diagram-canvas .flow-node-finished .djs-visual > :nth-child(1) {
  stroke: #52c41a !important;
  fill: #f6ffed !important;
}
.flow-diagram-canvas .flow-node-active .djs-visual > :nth-child(1) {
  stroke: #4a7fc1 !important;
  fill: #eaf1fb !important;
}
</style>

<style scoped>
.diagram-body {
  position: relative;
}
.flow-diagram-canvas {
  width: 100%;
  height: 460px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}
.diagram-loading {
  height: 460px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.diagram-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.diagram-legend {
  margin-top: 12px;
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.65);
}
.diagram-legend .dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  margin-right: 6px;
  vertical-align: middle;
}
.dot-finished {
  background: #f6ffed;
  border: 1.5px solid #52c41a;
}
.dot-active {
  background: #eaf1fb;
  border: 1.5px solid #4a7fc1;
}
</style>