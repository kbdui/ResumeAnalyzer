<script setup lang="ts">
import WorkflowStepCard from './WorkflowStepCard.vue'

const props = defineProps<{
  batchSize: number
  running: boolean
  taskRefId?: string
  status?: string
}>()

const emit = defineEmits<{
  (e: 'update:batchSize', value: number): void
  (e: 'run'): void
}>()
</script>

<template>
  <WorkflowStepCard
    :step="2"
    title="简历信息提取"
    description="text -> resume"
    :task-ref-id="props.taskRefId"
    :status="props.status"
  >
    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="extract batchSize">{{ props.batchSize }}</el-descriptions-item>
    </el-descriptions>
    <el-input-number
      :model-value="props.batchSize"
      :min="1"
      class="full-input"
      @update:model-value="(v: number | string | undefined) => emit('update:batchSize', Number(v || 1))"
    />
    <el-button type="primary" :loading="props.running" @click="emit('run')">执行 Step 2</el-button>
  </WorkflowStepCard>
</template>

<style scoped>
.full-input { width: 100%; }
</style>
