<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">系统架构</h2>
      <p class="page-subtitle">HRAgent 系统总体架构，悬停查看各模块职责说明</p>
    </div>

    <div class="arch-wrap">
      <div
        v-for="layer in layers"
        :key="layer.name"
        class="layer"
      >
        <div class="layer-head" :style="{ borderColor: layer.color }">
          <span class="layer-line" :style="{ background: layer.color }"></span>
          <span class="layer-name" :style="{ color: layer.color }">{{ layer.name }}</span>
          <span class="layer-desc">{{ layer.desc }}</span>
        </div>
        <div class="layer-modules" :class="{ 'vertical': layer.vertical }">
          <a-tooltip
            v-for="m in layer.modules"
            :key="m.name"
            placement="top"
          >
            <template #title>
              <div class="tip">{{ m.name }} — {{ m.desc }}</div>
            </template>
            <div class="module" :style="{ '--accent': layer.color }">
              <div class="module-name">{{ m.name }}</div>
              <div v-if="!layer.vertical" class="module-desc">{{ m.desc }}</div>
              <div v-if="m.tags" class="tags">
                <span v-for="t in m.tags" :key="t" class="tag">{{ t }}</span>
              </div>
            </div>
          </a-tooltip>
        </div>
      </div>
    </div>

    <div class="legend card">
      <strong>技术栈</strong>
      <div class="stack-list">
        <span v-for="s in stack" :key="s" class="stack">{{ s }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'ArchitecturePage' })

interface Module {
  name: string
  desc: string
  tags?: string[]
}
interface Layer {
  name: string
  desc: string
  color: string
  vertical?: boolean
  modules: Module[]
}

const layers: Layer[] = [
  {
    name: '前端展示层',
    desc: 'Vue 3 单页应用，哈希路由 + 角色化菜单',
    color: '#4a7fc1',
    modules: [
      { name: '页面组件 views', desc: '工作台 / 员工 / 简历 / 绩效 / 审批 / AI 助手等', tags: ['Vue 3'] },
      { name: '路由与菜单', desc: '路由守卫校验登录态，侧边菜单按角色过滤', tags: ['Vue Router'] },
      { name: '状态与请求', desc: 'Pinia 状态管理，axios 统一封装 R 响应与鉴权', tags: ['Pinia', 'Axios'] },
      { name: '可视化', desc: 'ECharts 图表驱动的数据看板与 Token 趋势', tags: ['ECharts 6'] },
      { name: 'UI 组件库', desc: 'Ant Design Vue 现代简约的组件体系', tags: ['AntDV'] }
    ]
  },
  {
    name: '接入与鉴权层',
    desc: '统一入口与安全防护',
    color: '#8a6fc2',
    modules: [
      { name: 'JWT 鉴权', desc: '登录签发令牌，AuthInterceptor 校验请求头 Authorization' },
      { name: 'AOP 切面', desc: '注解式能力：Redis 缓存 / 分布式锁 / 限流 / 防重复提交', tags: ['AOP'] },
      { name: '全局异常处理', desc: '统一错误码与 HttpMessageNotReadableException 兜底', tags: ['异常'] }
    ]
  },
  {
    name: '业务逻辑层',
    desc: 'Spring Boot 3 聚合各业务模块',
    color: '#2f9e6e',
    modules: [
      { name: 'AI 智能体', desc: 'LangChain4j + DeepSeek，ReAct 图 model→action→tool，按意图动态加载工具', tags: ['DeepSeek'] },
      { name: 'HR 业务服务', desc: '员工 / 岗位 / 简历 / 绩效 / 培训 / 能力标签', tags: ['Service'] },
      { name: 'Flowable 工作流', desc: '入职 / 离职 / 调岗 / 转正审批流程与轨迹追踪', tags: ['Flowable'] },
      { name: '合同审查 / 简历深度分析', desc: '基于大模型生成结构化审查与评估报告', tags: ['AI'] },
      { name: 'OCR 与文件', desc: '简历文本识别、对象存储与预览下载', tags: ['OCR'] }
    ]
  },
  {
    name: '数据与基础设施层',
    desc: '持久化、缓存与外部服务',
    color: '#d08a3e',
    vertical: true,
    modules: [
      { name: 'MySQL', desc: '业务数据持久化（MyBatis-Plus）' },
      { name: 'Redis + Redisson', desc: '缓存 / 分布式锁 / 限流计数（端口 16379）' },
      { name: '外部 AI 服务', desc: '阿里云 MaaS 大模型、Python 简历分析服务' },
      { name: '对象存储（COS）', desc: '简历附件等文件存储与预签名访问' }
    ]
  }
]

const stack = [
  'Java 17', 'Spring Boot 3.3', 'MyBatis-Plus', 'Flowable 7',
  'Vue 3.5', 'TypeScript', 'Ant Design Vue', 'ECharts',
  'MySQL 5.7', 'Redis 6.2', 'Docker', 'Docker Compose'
]
</script>

<style scoped>
.arch-wrap {
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin-bottom: 20px;
}
.layer {
  background: #fff;
  border: 1px solid #eef0f3;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.layer-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 8px;
  border-left: 3px solid;
  margin-bottom: 14px;
}
.layer-line {
  width: 0;
}
.layer-name {
  font-size: 15px;
  font-weight: 600;
}
.layer-desc {
  font-size: 12px;
  color: #9ca3af;
}
.layer-modules {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.layer-modules.vertical {
  flex-direction: column;
}
.module {
  flex: 1 1 200px;
  min-width: 180px;
  max-width: 260px;
  border: 1px solid #eef0f3;
  border-top: 2px solid var(--accent, #4a7fc1);
  border-radius: 8px;
  padding: 10px 12px;
  background: #fafbfc;
  transition: all 0.15s;
  cursor: default;
}
.module:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  border-color: var(--accent, #4a7fc1);
}
.module-name {
  font-weight: 600;
  font-size: 13px;
  color: #1f2937;
}
.module-desc {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
  line-height: 1.5;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}
.tag {
  font-size: 11px;
  color: #4a7fc1;
  background: #e8f0fe;
  padding: 1px 6px;
  border-radius: 4px;
}
.legend {
  padding: 16px 18px;
  border-radius: 12px;
}
.stack-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.stack {
  font-size: 12px;
  color: #374151;
  background: #f1f3f5;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid #eef0f3;
}
.tip {
  max-width: 260px;
}
</style>