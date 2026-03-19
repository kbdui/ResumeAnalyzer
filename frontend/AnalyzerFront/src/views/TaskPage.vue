<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import TaskSelector from '@/components/TaskSelector.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import JsonViewer from '@/components/JsonViewer.vue'
import { queryMatchTask, submitMatchTask } from '@/api'
import type { PythonTaskItem, PythonTaskResultPayload } from '@/api/types'

const taskId = ref('')
const jdText = ref('')
const running = ref(false)
const lastResult = ref<PythonTaskResultPayload | null>(null)
let timer: number | null = null

const items = computed<PythonTaskItem[]>(() => lastResult.value?.result?.results?.items || [])
const taskStatus = computed(() => lastResult.value?.status || 'UNKNOWN')

function stopPolling() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
  running.value = false
}

async function pollOnce() {
  if (!taskId.value) return
  try {
    const data = await queryMatchTask(taskId.value)
    lastResult.value = data
    if (data.status === 'done') {
      stopPolling()
      ElMessage.success('筛选任务已完成')
    } else if (data.status === 'failed') {
      stopPolling()
      ElMessage.error(data.error || '筛选任务失败')
    }
  } catch (error) {
    stopPolling()
    ElMessage.error((error as Error).message || '任务轮询失败')
  }
}

async function startTask() {
  if (!taskId.value) {
    ElMessage.warning('请先选择 task')
    return
  }
  if (!jdText.value.trim()) {
    ElMessage.warning('请输入 jdText')
    return
  }
  try {
    await submitMatchTask({
      taskId: taskId.value,
      jdText: jdText.value.trim(),
    })
    ElMessage.success('提交成功，开始轮询任务进度')
    stopPolling()
    running.value = true
    await pollOnce()
    timer = window.setInterval(pollOnce, 2000)
  } catch (error) {
    ElMessage.error((error as Error).message || '提交筛选任务失败')
  }
}

onBeforeUnmount(stopPolling)
</script>

<template>
  <el-space direction="vertical" class="full-width page-stack" :size="18">
    <el-card class="panel-card">
      <template #header>
        <div class="header-row">
          <el-text tag="b" class="panel-title">2. 选择 Task 并执行筛选</el-text>
          <span class="muted-text">执行 Python 两阶段筛选并自动轮询状态</span>
        </div>
      </template>
      <el-space direction="vertical" class="full-width section-stack">
        <TaskSelector v-model="taskId" />
        <el-input
          v-model="jdText"
          type="textarea"
          :rows="5"
          placeholder="请输入岗位 JD 文本"
        />
        <el-space>
          <el-button type="primary" @click="startTask">开始执行</el-button>
          <StatusBadge :status="taskStatus" />
          <el-icon v-if="running" class="is-loading"><Loading /></el-icon>
        </el-space>
      </el-space>
    </el-card>

    <el-card class="panel-card">
      <template #header>
        <div class="header-row">
          <el-text tag="b" class="panel-title">3. Task 执行结果</el-text>
          <span class="muted-text">A+B 展示：结构化表格 + 原始 JSON</span>
        </div>
      </template>
      <el-tabs>
        <el-tab-pane label="结果表格">
          <el-table :data="items" stripe border style="width: 100%" class="result-table">
            <el-table-column prop="resume_id" label="resume_id" min-width="180" />
            <el-table-column prop="file_name" label="file_name" min-width="180" />
            <el-table-column prop="final_score" label="final_score" min-width="120" />
            <el-table-column prop="embedding_score" label="embedding_score" min-width="140" />
            <el-table-column prop="recall_score" label="recall_score" min-width="120" />
            <el-table-column prop="keyword_coverage" label="keyword_coverage" min-width="140" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="原始JSON">
          <JsonViewer :data="lastResult" />
        </el-tab-pane>
      </el-tabs>
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
