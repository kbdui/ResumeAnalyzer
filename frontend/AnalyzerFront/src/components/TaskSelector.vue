<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listTasks } from '@/api'
import type { TaskItem } from '@/api/types'

const props = defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const loading = ref(false)
const tasks = ref<TaskItem[]>([])

async function refreshTasks(silent = false) {
  loading.value = true
  try {
    tasks.value = await listTasks()
  } catch (error) {
    tasks.value = []
    if (!silent) {
      ElMessage.warning((error as Error).message || '任务列表加载失败')
    }
  } finally {
    loading.value = false
  }
}

function updateTaskId(value: string) {
  emit('update:modelValue', value)
}

onMounted(() => {
  void refreshTasks(true)
})
</script>

<template>
  <el-space direction="vertical" class="task-selector">
    <div class="task-header">
      <el-text tag="b" class="panel-title">任务选择</el-text>
      <el-button size="small" plain :loading="loading" @click="refreshTasks">刷新</el-button>
    </div>
    <el-select
      class="task-select"
      filterable
      clearable
      placeholder="请选择 taskId"
      :model-value="props.modelValue"
      @update:model-value="updateTaskId"
    >
      <el-option
        v-for="task in tasks"
        :key="task.id"
        :label="`${task.taskId}（简历数: ${task.resumeCount}）`"
        :value="task.taskId"
      />
    </el-select>
  </el-space>
</template>

<style scoped>
.task-selector {
  width: 100%;
}

.task-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.task-select {
  width: 100%;
}
</style>
