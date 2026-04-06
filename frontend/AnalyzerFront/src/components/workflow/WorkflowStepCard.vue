<script setup lang="ts">
import StatusBadge from '@/components/StatusBadge.vue'

defineProps<{
  step: number
  title: string
  description?: string
  taskRefId?: string
  status?: string
}>()
</script>

<template>
  <el-card class="step-card">
    <template #header>
      <div class="step-header">
        <div class="left">
          <el-tag type="info" effect="plain">Step {{ step }}</el-tag>
          <span class="title">{{ title }}</span>
          <span v-if="description" class="desc">{{ description }}</span>
        </div>
        <div class="right">
          <StatusBadge v-if="status" :status="status" />
        </div>
      </div>
    </template>

    <div class="content">
      <slot />
    </div>

    <el-descriptions v-if="taskRefId" :column="1" border size="small" class="meta">
      <el-descriptions-item label="任务ID">{{ taskRefId }}</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<style scoped>
.step-card { height: 100%; }
.step-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.left { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.title { font-weight: 600; color: #1f2a44; }
.desc { color: #7c88a5; font-size: 12px; }
.content { display: flex; flex-direction: column; gap: 12px; }
.meta { margin-top: 12px; }
</style>
