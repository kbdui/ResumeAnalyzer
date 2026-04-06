<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import TaskSelector from '@/components/TaskSelector.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import JsonViewer from '@/components/JsonViewer.vue'
import { listAnalysisByTask, queryAnalyzeTask, submitAnalyzeTaskWithBatch } from '@/api'
import type { AnalyzeTaskStatus, AnalysisItem } from '@/api/types'

const taskId = ref('')
const analyzeTaskId = ref('')
const batchSize = ref(5)
const status = ref<AnalyzeTaskStatus | null>(null)
const rows = ref<AnalysisItem[]>([])
const running = ref(false)

const parsedRows = computed(() =>
  rows.value.map((r) => {
    let parsed: Record<string, unknown> = {}
    try {
      parsed = JSON.parse(r.analysisJson) as Record<string, unknown>
    } catch {
      parsed = {}
    }
    return { ...r, parsed }
  }),
)

function sleep(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

async function pollDone() {
  while (true) {
    const s = await queryAnalyzeTask(analyzeTaskId.value)
    status.value = s
    if (s.status === 'SUCCESS' || s.status === 'PARTIAL_SUCCESS') return
    if (s.status === 'FAILED') throw new Error(s.error || '分析失败')
    await sleep(2000)
  }
}

async function loadRows() {
  rows.value = await listAnalysisByTask(taskId.value)
}

async function runAnalyze() {
  if (!taskId.value) {
    ElMessage.warning('请先选择 task')
    return
  }
  running.value = true
  try {
    const submit = await submitAnalyzeTaskWithBatch(taskId.value, Math.floor(batchSize.value))
    analyzeTaskId.value = submit.analyzeTaskId
    await pollDone()
    await loadRows()
    ElMessage.success('分析完成')
  } catch (error) {
    ElMessage.error((error as Error).message || '分析失败')
  } finally {
    running.value = false
  }
}

async function refreshOnly() {
  if (!taskId.value) {
    ElMessage.warning('请先选择 task')
    return
  }
  try {
    await loadRows()
    ElMessage.success('已刷新')
  } catch (error) {
    ElMessage.error((error as Error).message || '刷新失败')
  }
}
</script>

<template>
  <div class="page">
    <el-card class="panel-card">
      <template #header>
        <div class="header-row">
          <div class="header-left"><el-icon><MagicStick /></el-icon><span>大模型最终评估</span></div>
        </div>
      </template>
      <TaskSelector v-model="taskId" />
      <el-space class="mt12">
        <span>batchSize</span>
        <el-input-number v-model="batchSize" :min="1" />
        <el-button type="primary" :loading="running" @click="runAnalyze">提交并轮询</el-button>
        <el-button @click="refreshOnly">仅查询结果</el-button>
      </el-space>
      <el-descriptions :column="2" border class="mt12">
        <el-descriptions-item label="AnalyzeTaskId">{{ analyzeTaskId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusBadge :status="status?.status" /></el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="panel-card" v-if="rows.length > 0">
      <template #header><span>评估结果</span></template>
      <el-table :data="parsedRows" stripe max-height="420">
        <el-table-column prop="resumeId" label="简历ID" min-width="180" />
        <el-table-column label="等级" width="170">
          <template #default="{ row }">{{ row.parsed.overall_level || '-' }}</template>
        </el-table-column>
        <el-table-column label="分数" width="120">
          <template #default="{ row }">{{ row.parsed.overall_score ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="总结" min-width="420" show-overflow-tooltip>
          <template #default="{ row }">{{ row.parsed.summary || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="panel-card" v-if="rows.length > 0">
      <template #header><span>原始 JSON</span></template>
      <JsonViewer :data="rows" />
    </el-card>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 20px; }
.header-row { display: flex; align-items: center; justify-content: space-between; }
.header-left { display: flex; align-items: center; gap: 8px; }
.mt12 { margin-top: 12px; }
</style>
