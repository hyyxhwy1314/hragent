<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">合同审查</h2>
      <p class="page-subtitle">粘贴或上传合同文本，AI 自动识别潜在法律风险并给出修改建议</p>
    </div>

    <a-row :gutter="16">
      <!-- 输入区 -->
      <a-col :xs="24" :lg="12">
        <div class="card panel">
          <div class="panel-head">
            <strong>合同内容</strong>
            <a-space>
              <a-upload
                :show-upload-list="false"
                :before-upload="onUploadText"
                accept=".txt,.md"
              >
                <a-button size="small" :icon="h(FileTextOutlined)">导入文本</a-button>
              </a-upload>
              <a-button size="small" @click="fillSample">填入示例</a-button>
            </a-space>
          </div>
          <a-input
            v-model:value="form.contractName"
            placeholder="合同名称（可选），例如：员工劳动合同"
            style="margin-bottom: 10px"
            maxlength="60"
          />
          <a-textarea
            v-model:value="form.content"
            :rows="14"
            placeholder="在此粘贴合同全文…"
            :maxlength="20000"
            show-count
            class="content-area"
          />
          <div class="actions">
            <a-button
              type="primary"
              :icon="h(SafetyCertificateOutlined)"
              :loading="reviewing"
              :disabled="!form.content?.trim()"
              @click="onReview"
            >
              开始审查
            </a-button>
            <span v-if="form.content?.length" class="hint">字数 {{ form.content.length }}</span>
          </div>
        </div>
      </a-col>

      <!-- 结果区 -->
      <a-col :xs="24" :lg="12">
        <div class="card panel">
          <div class="panel-head">
            <strong>审查报告</strong>
            <a-space v-if="result?.success && result.totalIssues !== undefined">
              <a-tag v-if="result.riskLevel" :color="riskColor(result.riskLevel)">
                综合风险：{{ result.riskLevel }}
              </a-tag>
              <a-tag color="geekblue">共 {{ result.totalIssues }} 处风险</a-tag>
            </a-space>
          </div>

          <a-empty
            v-if="!result && !reviewing"
            description="暂未审查，请输入合同内容后点击「开始审查」"
            style="margin-top: 48px"
          />
          <a-spin :spinning="reviewing">
            <div v-if="result && !reviewing">
              <a-alert
                v-if="!result.success"
                type="error"
                show-icon
                :message="result.summary || '审查失败'"
                style="margin-bottom: 14px"
              />
              <template v-else>
                <div class="summary-block">
                  <div class="summary-label">总体评价</div>
                  <div class="summary-text">{{ result.summary || '-' }}</div>
                </div>

                <a-empty v-if="!result.issues?.length" description="未识别到明显法律风险" />
                <div v-else class="issues-list">
                  <div
                    v-for="(issue, idx) in result.issues"
                    :key="idx"
                    class="issue-item"
                  >
                    <div class="issue-head">
                      <a-tag :color="severityColor(issue.severity)">{{ issue.severity || '低' }}</a-tag>
                      <span class="issue-type">{{ issue.issueType || '其他' }}</span>
                    </div>
                    <div class="issue-clause" v-if="issue.contractClause">
                      涉及条款：{{ issue.contractClause }}
                    </div>
                    <div class="issue-desc">{{ issue.description }}</div>
                    <div v-if="issue.suggestion" class="issue-suggestion">
                      <span class="sg-label">建议</span>{{ issue.suggestion }}
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </a-spin>
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, h } from 'vue'
import { message } from 'ant-design-vue'
import { FileTextOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import { contractReviewApi, type ContractReviewResult } from '@/api/modules/contractReview'

defineOptions({ name: 'ContractReviewPage' })

const form = reactive<{ contractName?: string; content?: string }>({ contractName: '', content: '' })
const reviewing = ref(false)
const result = ref<ContractReviewResult | null>(null)

function riskColor(level?: string): string {
  if (level === '高') return 'red'
  if (level === '中') return 'orange'
  return 'green'
}
function severityColor(level?: string): string {
  if (level === '高') return 'red'
  if (level === '中') return 'orange'
  return 'blue'
}

async function onReview() {
  if (!form.content?.trim()) return
  reviewing.value = true
  result.value = null
  try {
    result.value = await contractReviewApi.review(form.content, form.contractName)
    if (result.value?.success) message.success('合同审查完成')
  } catch {
    /* 错误提示已由拦截器统一处理 */
  } finally {
    reviewing.value = false
  }
}

/** 导入 .txt 文本文件内容 */
function onUploadText(file: File): boolean {
  if (file.size > 1024 * 1024) {
    message.error('文本文件不能超过 1MB')
    return false
  }
  const reader = new FileReader()
  reader.onload = () => {
    form.content = String(reader.result || '')
    message.success('已导入文本')
  }
  reader.readAsText(file, 'utf-8')
  return false
}

function fillSample() {
  form.contractName = '员工劳动合同'
  form.content = `甲方（用人单位）：XX科技有限公司
乙方（员工）：张某某，身份证号：110101199001011234

第一条 合同期限
本合同期限为固定期限：自 2024年1月1日起至 2026年12月31日止。

第二条 工作内容与地点
乙方在甲方担任 软件工程师 岗位，工作地点为甲方所在地。甲方可根据经营需要调整乙方岗位，乙方应当服从。

第三条 工作时间与薪酬
实行标准工时制。乙方的月工资为税前 15000 元，其中基本工资 6000 元，绩效工资 9000 元，绩效工资依据考核结果浮动发放。
工资于每月 15 日发放，甲方有权根据经营状况调整乙方薪酬。

第四条 福利与社会保险
甲方依法为乙方缴纳社会保险和住房公积金，缴费基数以乙方基本工资为准，绩效工资不纳入社保缴费基数。

第五条 保密与竞业限制
乙方在离职后 2 年内不得从事与甲方有竞争关系的业务，也不得自营或为他人经营与甲方经营范围相同或类似的业务。
竞业限制经济补偿按基本工资的 30% 按月支付，支付期限 12 个月。
乙方违反保密义务或竞业限制约定，应向甲方支付相当于 12 个月工资的违约金。

第六条 合同解除与违约责任
乙方主动离职应当提前 30 日书面通知甲方。未经甲方批准擅自离职的，乙方应向甲方支付 1 个月工资作为违约金。
合同试用期内，甲方认为乙方不符合录用条件的，可以解除合同，无需提前通知，也无需支付任何补偿。

第七条 争议解决
双方发生劳动争议，由甲方所在地人民法院管辖。

甲方（盖章）：            乙方（签字）：
日期：                    日期：`
}
</script>

<style scoped>
.card {
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  padding: 18px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
  color: #1f2937;
}
.content-area {
  background: #fafbfc;
  font-size: 13px;
}
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
.hint {
  font-size: 12px;
  color: #9ca3af;
}
.summary-block {
  background: #f0f7ff;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 16px;
}
.summary-label {
  font-size: 12px;
  color: #4a7fc1;
  font-weight: 600;
  margin-bottom: 6px;
}
.summary-text {
  font-size: 14px;
  line-height: 1.7;
  color: #1f2937;
  white-space: pre-wrap;
}
.issues-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.issue-item {
  border: 1px solid #eef0f3;
  border-radius: 8px;
  padding: 12px 14px;
  background: #fff;
}
.issue-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.issue-type {
  font-weight: 600;
  font-size: 13px;
  color: #1f2937;
}
.issue-clause {
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}
.issue-desc {
  font-size: 13px;
  line-height: 1.6;
  color: #374151;
}
.issue-suggestion {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: #1e6f4e;
  background: #ecfdf5;
  border-radius: 6px;
  padding: 8px 10px;
}
.sg-label {
  font-weight: 600;
  margin-right: 6px;
}
</style>