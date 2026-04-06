<script setup lang="ts">
import WorkflowStepCard from './WorkflowStepCard.vue'

const props = defineProps<{
  running: boolean
  taskRefId?: string
  status?: string
}>()

const emit = defineEmits<{
  (e: 'run'): void
}>()
</script>

<template>
  <WorkflowStepCard
    :step="3"
    title="硬过滤"
    description="JD + resume -> task_resume(pass)"
    :task-ref-id="props.taskRefId"
    :status="props.status"
  >
    <el-text type="info">本步骤使用流程上下文中的 JD 文本</el-text>
    <el-button type="primary" :loading="props.running" @click="emit('run')">执行 Step 3</el-button>
  </WorkflowStepCard>
</template>
