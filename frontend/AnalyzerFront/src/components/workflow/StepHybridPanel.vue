<script setup lang="ts">
import WorkflowStepCard from './WorkflowStepCard.vue'

const props = defineProps<{
  topK: number
  recallK: number
  running: boolean
  status?: string
}>()

const emit = defineEmits<{
  (e: 'update:topK', value: number): void
  (e: 'update:recallK', value: number): void
  (e: 'run'): void
}>()
</script>

<template>
  <WorkflowStepCard
    :step="4"
    title="召回筛选"
    description="通过硬过滤的简历 -> hybrid_result"
    :status="props.status"
  >
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
    <el-button type="primary" :loading="props.running" @click="emit('run')">执行 Step 4</el-button>
  </WorkflowStepCard>
</template>

<style scoped>
.full-input { width: 100%; }
</style>
