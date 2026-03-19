<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import TaskSelector from '@/components/TaskSelector.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import JsonViewer from '@/components/JsonViewer.vue'
import { listResumeByTask, queryAnalyzeTask, submitAnalyzeTask } from '@/api'
import type { AnalyzeTaskStatus, TaskResumeMainView } from '@/api/types'

const taskId = ref('')
const analyzeTaskId = ref('')
const status = ref<AnalyzeTaskStatus | null>(null)
const analyzing = ref(false)
const resumeRows = ref<TaskResumeMainView[]>([])
let timer: number | null = null

const progress = computed(() => {
  if (!status.value || !status.value.total) return 0
  return Math.round(((status.value.successCount + status.value.failedCount) / status.value.total) * 100)
})

function stopPolling() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
  analyzing.value = false
}

async function loadAnalyzeResult() {
  if (!taskId.value) return
  resumeRows.value = await listResumeByTask(taskId.value)
}

async function pollAnalyze() {
  if (!analyzeTaskId.value) return
  try {
    status.value = await queryAnalyzeTask(analyzeTaskId.value)
    if (status.value.status === 'SUCCESS' || status.value.status === 'FAILED') {
      stopPolling()
      await loadAnalyzeResult()
      if (status.value.status === 'SUCCESS') {
        ElMessage.success('大模型分析任务已完成')
      } else {
        ElMessage.error(status.value.error || '大模型分析任务失败')
      }
    }
  } catch (error) {
    stopPolling()
    ElMessage.error((error as Error).message || '分析任务轮询失败')
  }
}

async function startAnalyze() {
  if (!taskId.value) {
    ElMessage.warning('请先选择 task')
    return
  }
  try {
    const response = await submitAnalyzeTask(taskId.value)
    analyzeTaskId.value = response.analyzeTaskId
    ElMessage.success(response.message || '提交成功')
    stopPolling()
    analyzing.value = true
    await pollAnalyze()
    timer = window.setInterval(pollAnalyze, 2000)
  } catch (error) {
    ElMessage.error((error as Error).message || '提交分析任务失败')
  }
}

onBeforeUnmount(stopPolling)
</script>

<template>
  <el-space direction="vertical" class="full-width page-stack" :size="18">
    <el-card class="panel-card">
      <template #header>
        <div class="header-row">
          <el-text tag="b" class="panel-title">4. 提交 Task 给大模型分析</el-text>
          <span class="muted-text">异步执行，状态自动轮询</span>
        </div>
      </template>
      <el-space direction="vertical" class="full-width section-stack">
        <TaskSelector v-model="taskId" />
        <el-space>
          <el-button type="primary" size="large" @click="startAnalyze">提交分析</el-button>
          <el-text>analyzeTaskId: {{ analyzeTaskId || '-' }}</el-text>
          <StatusBadge :status="status?.status" />
          <el-icon v-if="analyzing" class="is-loading"><Loading /></el-icon>
        </el-space>
        <el-progress :percentage="progress" :stroke-width="16" striped striped-flow />
      </el-space>
    </el-card>

    <el-card class="panel-card">
      <template #header>
        <el-text tag="b" class="panel-title">分析任务状态（原始）</el-text>
      </template>
      <JsonViewer :data="status" />
    </el-card>

    <el-card class="panel-card">
      <template #header>
        <div class="header-row">
          <el-text tag="b" class="panel-title">5. 分析结果（按 task 聚合）</el-text>
          <span class="muted-text">显示排序、基础画像、技能摘要</span>
        </div>
      </template>
      <el-table :data="resumeRows" stripe border style="width: 100%" class="result-table">
        <el-table-column prop="relationId" label="relationId" min-width="100" />
        <el-table-column prop="resumeId" label="resumeId" min-width="100" />
        <el-table-column prop="rankNo" label="rankNo" min-width="80" />
        <el-table-column prop="finalScore" label="finalScore" min-width="120" />
        <el-table-column label="姓名" min-width="120">
          <template #default="{ row }">{{ row.resume?.personalInfo?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="联系方式" min-width="150">
          <template #default="{ row }">{{ row.resume?.personalInfo?.contact || '-' }}</template>
        </el-table-column>
        <el-table-column label="技能" min-width="240">
          <template #default="{ row }">{{ (row.resume?.skills || []).join(', ') || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </el-space>
</template>

<style scoped>
.full-width {
  width: 100%;
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-stack {
  padding-bottom: 8px;
}

.section-stack {
  gap: 14px;
}

.result-table :deep(th.el-table__cell) {
  background: #f2f6ff;
}
</style>
