<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataAnalysis, Search } from '@element-plus/icons-vue'
import TaskSelector from '@/components/TaskSelector.vue'
import JsonViewer from '@/components/JsonViewer.vue'
import { listAnalysisByTask, queryMatchTask } from '@/api'
import type { AnalysisItem, PythonTaskItem, PythonTaskResultPayload } from '@/api/types'

const taskId = ref('')
const loading = ref(false)
const matchResult = ref<PythonTaskResultPayload | null>(null)
const analysisRows = ref<AnalysisItem[]>([])

const matchItems = computed<PythonTaskItem[]>(() => matchResult.value?.result?.results?.items || [])
const parsedAnalysisRows = computed(() =>
  analysisRows.value.map((r) => {
    let parsed: Record<string, unknown> = {}
    try {
      parsed = JSON.parse(r.analysisJson) as Record<string, unknown>
    } catch {
      parsed = {}
    }
    return { ...r, parsed }
  }),
)

async function queryHistory() {
  if (!taskId.value) {
    ElMessage.warning('请先选择 task')
    return
  }
  loading.value = true
  const errors: string[] = []
  try {
    matchResult.value = await queryMatchTask(taskId.value)
  } catch (error) {
    matchResult.value = null
    errors.push(`召回筛选结果查询失败：${(error as Error).message}`)
  }
  try {
    analysisRows.value = await listAnalysisByTask(taskId.value)
  } catch (error) {
    analysisRows.value = []
    errors.push(`大模型评估结果查询失败：${(error as Error).message}`)
  }
  loading.value = false
  if (errors.length > 0) {
    ElMessage.warning(errors[0]!)
  } else {
    ElMessage.success('历史记录查询完成')
  }
}
</script>

<template>
  <div class="history-page">
    <el-card class="panel-card">
      <template #header>
        <div class="header-left">
          <el-icon><DataAnalysis /></el-icon>
          <span>历史记录查询</span>
        </div>
      </template>
      <TaskSelector v-model="taskId" />
      <el-button class="mt12" type="primary" :loading="loading" @click="queryHistory">
        <el-icon><Search /></el-icon>
        查询
      </el-button>
    </el-card>

    <el-card class="panel-card">
      <template #header><span>召回筛选结果</span></template>
      <el-table :data="matchItems" stripe max-height="360">
        <el-table-column type="index" width="60" />
        <el-table-column prop="resume_id" label="简历ID" min-width="180" />
        <el-table-column prop="file_name" label="文件名" min-width="180" />
        <el-table-column prop="final_score" label="综合分" width="120" />
      </el-table>
    </el-card>

    <el-card class="panel-card">
      <template #header><span>大模型评估结果</span></template>
      <el-table :data="parsedAnalysisRows" stripe max-height="360">
        <el-table-column prop="resumeId" label="简历ID" min-width="180" />
        <el-table-column label="等级" width="160">
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

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card class="panel-card"><template #header><span>召回结果 JSON</span></template><JsonViewer :data="matchResult" /></el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="panel-card"><template #header><span>评估结果 JSON</span></template><JsonViewer :data="analysisRows" /></el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.history-page { display: flex; flex-direction: column; gap: 20px; }
.header-left { display: flex; align-items: center; gap: 8px; }
.mt12 { margin-top: 12px; }
</style>

