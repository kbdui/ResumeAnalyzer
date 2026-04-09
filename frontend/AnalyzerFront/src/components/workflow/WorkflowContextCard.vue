<script setup lang="ts">
import { Loading, Search } from '@element-plus/icons-vue'
import TaskSelector from '@/components/TaskSelector.vue'

const props = defineProps<{
  taskId: string
  jdText: string
  runningPipeline: boolean
  runningAnyStep: boolean
}>()

const emit = defineEmits<{
  (e: 'update:taskId', value: string): void
  (e: 'update:jdText', value: string): void
  (e: 'run-all'): void
  (e: 'refresh'): void
}>()
</script>

<template>
  <el-card class="panel-card">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <el-icon class="header-icon"><Search /></el-icon>
          <span class="panel-title">流程上下文</span>
        </div>
        <el-space>
          <el-button
            type="primary"
            :loading="props.runningPipeline"
            :disabled="props.runningAnyStep && !props.runningPipeline"
            @click="emit('run-all')"
          >
            <el-icon v-if="props.runningPipeline"><Loading class="is-loading" /></el-icon>
            一键执行 Step2-5
          </el-button>
          <el-button @click="emit('refresh')">刷新结果</el-button>
        </el-space>
      </div>
    </template>
    <TaskSelector :model-value="props.taskId" @update:model-value="(v) => emit('update:taskId', v)" />
    <el-input
      :model-value="props.jdText"
      type="textarea"
      :rows="5"
      class="mt12"
      placeholder="请输入岗位 JD 文本（用于硬过滤和召回筛选）"
      @update:model-value="(v: string) => emit('update:jdText', String(v || ''))"
    />
  </el-card>
</template>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.header-left { display: flex; align-items: center; gap: 8px; }
.header-icon { color: #5b8cff; font-size: 20px; }
.mt12 { margin-top: 12px; }
</style>
