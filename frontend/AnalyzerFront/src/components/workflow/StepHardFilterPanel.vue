<script setup lang="ts">
import { computed, ref } from 'vue'
import JsonViewer from '@/components/JsonViewer.vue'
import type { AnalyzeTaskStatus } from '@/api/types'
import WorkflowStepCard from './WorkflowStepCard.vue'

const props = defineProps<{
  currentTaskId?: string
  batchSize: number
  running: boolean
  actionDisabled: boolean
  taskRefId?: string
  statusData?: AnalyzeTaskStatus | null
}>()

const emit = defineEmits<{
  (e: 'update:batchSize', value: number): void
  (e: 'run'): void
}>()

const showDialog = ref(false)
const progress = computed(() => {
  const s = props.statusData
  if (!s || !s.total) return 0
  return Math.round(((s.successCount + s.failedCount) / s.total) * 100)
})
</script>

<template>
  <WorkflowStepCard
    :step="3"
    title="硬过滤"
    description="JD + resume -> task_resume(pass)"
    :current-task-id="props.currentTaskId"
    :task-ref-id="props.taskRefId"
    :status="props.statusData?.status"
  >
    <el-descriptions v-if="props.statusData" :column="2" border size="small">
      <el-descriptions-item label="总数">{{ props.statusData.total }}</el-descriptions-item>
      <el-descriptions-item label="进度">{{ progress }}%</el-descriptions-item>
      <el-descriptions-item label="成功">{{ props.statusData.successCount }}</el-descriptions-item>
      <el-descriptions-item label="失败">{{ props.statusData.failedCount }}</el-descriptions-item>
    </el-descriptions>
    <el-progress v-if="props.statusData" :percentage="progress" :stroke-width="10" />
    <el-alert
      v-if="props.statusData?.error"
      title="执行错误"
      type="error"
      :description="props.statusData.error"
      show-icon
      :closable="false"
    />
    <el-text type="info">本步骤使用流程上下文中的 JD 文本</el-text>
    <div class="batch-row">
      <el-text type="info">hard-filter batchSize</el-text>
      <el-input-number
        :model-value="props.batchSize"
        :min="1"
        class="batch-input"
        @update:model-value="(v: number | string | undefined) => emit('update:batchSize', Number(v || 1))"
      />
    </div>
    <el-space wrap>
      <el-button type="primary" :loading="props.running" :disabled="props.actionDisabled" @click="emit('run')">
        执行 Step 3
      </el-button>
      <el-button v-if="props.statusData" @click="showDialog = true">查看详情</el-button>
    </el-space>
  </WorkflowStepCard>

  <el-dialog v-model="showDialog" title="Step 3 执行详情" width="680px">
    <JsonViewer :data="props.statusData" />
  </el-dialog>
</template>

<style scoped>
.batch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.batch-input {
  width: 220px;
}
</style>
