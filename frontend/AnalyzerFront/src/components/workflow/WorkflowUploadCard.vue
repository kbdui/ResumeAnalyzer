<script setup lang="ts">
import { ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadResponse } from '@/api/types'

defineProps<{
  loading: boolean
  uploadResult: UploadResponse | null
}>()

const emit = defineEmits<{
  (e: 'submit', file: File): void
}>()

const selectedFile = ref<File | null>(null)

function handleFileChange(file: File) {
  selectedFile.value = file
  return false
}

function submit() {
  if (!selectedFile.value) {
    return
  }
  emit('submit', selectedFile.value)
}
</script>

<template>
  <el-card class="panel-card">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <el-icon class="header-icon"><UploadFilled /></el-icon>
          <span class="panel-title">Step 1 上传简历</span>
        </div>
        <el-button type="primary" :loading="loading" @click="submit">上传 zip</el-button>
      </div>
    </template>
    <el-upload
      drag
      :auto-upload="false"
      :show-file-list="true"
      :on-change="(f: { raw: File }) => handleFileChange(f.raw!)"
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div>拖拽 zip 文件到此处，或点击选择</div>
    </el-upload>
    <el-descriptions v-if="uploadResult" :column="2" border class="mt12">
      <el-descriptions-item label="Task ID">{{ uploadResult.taskId }}</el-descriptions-item>
      <el-descriptions-item label="成功入库">{{ uploadResult.savedCount }}/{{ uploadResult.resumeCount }}</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.header-left { display: flex; align-items: center; gap: 8px; }
.header-icon { color: #5b8cff; font-size: 20px; }
.upload-icon { font-size: 42px; color: #5b8cff; }
.mt12 { margin-top: 12px; }
</style>
