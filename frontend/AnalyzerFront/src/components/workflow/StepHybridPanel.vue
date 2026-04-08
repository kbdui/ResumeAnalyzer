<script setup lang="ts">
import { computed, ref } from 'vue'
import JsonViewer from '@/components/JsonViewer.vue'
import type { PythonTaskResultPayload } from '@/api/types'
import WorkflowStepCard from './WorkflowStepCard.vue'

const props = defineProps<{
  currentTaskId?: string
  topK: number
  recallK: number
  running: boolean
  resultData?: PythonTaskResultPayload | null
}>()

const emit = defineEmits<{
  (e: 'update:topK', value: number): void
  (e: 'update:recallK', value: number): void
  (e: 'run'): void
}>()

const showDialog = ref(false)
const statusText = computed(() => props.resultData?.status?.toUpperCase() || '')
const summaryText = computed(() => {
  const summary = props.resultData?.result?.summary
  if (!summary) return '-'
  return `候选 ${summary.total_resumes}，召回 ${summary.recall_count}，TopK ${summary.top_k}`
})
</script>

<template>
  <WorkflowStepCard
    :step="4"
    title="召回筛选"
    description="通过硬过滤的简历 -> hybrid_result"
    :current-task-id="props.currentTaskId"
    :status="statusText"
  >
    <el-descriptions v-if="props.resultData" :column="2" border size="small">
      <el-descriptions-item label="状态">{{ statusText }}</el-descriptions-item>
      <el-descriptions-item label="摘要">{{ summaryText }}</el-descriptions-item>
    </el-descriptions>
    <el-alert
      v-if="props.resultData?.error"
      title="执行错误"
      type="error"
      :description="props.resultData.error"
      show-icon
      :closable="false"
    />
    <el-row :gutter="12">
      <el-col :span="12">
        <el-text type="info">topK</el-text>
        <el-input-number
          :model-value="props.topK"
          :min="1"
          class="full-input"
          @update:model-value="(v: number | string | undefined) => emit('update:topK', Number(v || 1))"
        />
      </el-col>
      <el-col :span="12">
        <el-text type="info">recallK</el-text>
        <el-input-number
          :model-value="props.recallK"
          :min="1"
          class="full-input"
          @update:model-value="(v: number | string | undefined) => emit('update:recallK', Number(v || 1))"
        />
      </el-col>
    </el-row>
    <el-space wrap>
      <el-button type="primary" :loading="props.running" @click="emit('run')">执行 Step 4</el-button>
      <el-button v-if="props.resultData" @click="showDialog = true">查看详情</el-button>
    </el-space>
  </WorkflowStepCard>

  <el-dialog v-model="showDialog" title="Step 4 执行详情" width="760px">
    <JsonViewer :data="props.resultData" />
  </el-dialog>
</template>

<style scoped>
.full-input { width: 100%; }
</style>
