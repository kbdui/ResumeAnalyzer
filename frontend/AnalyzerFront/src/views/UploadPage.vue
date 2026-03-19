<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { uploadZip } from '@/api'
import type { UploadResponse } from '@/api/types'

const selectedFile = ref<File | null>(null)
const loading = ref(false)
const latestResult = ref<UploadResponse | null>(null)

function handleFileChange(file: File) {
  selectedFile.value = file
  return false
}

async function submitUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择 zip 文件')
    return
  }
  loading.value = true
  try {
    latestResult.value = await uploadZip(selectedFile.value)
    ElMessage.success(`上传成功，taskId: ${latestResult.value.taskId}`)
  } catch (error) {
    ElMessage.error((error as Error).message || '上传失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <el-card class="panel-card">
    <template #header>
      <div class="header-row">
        <el-text tag="b" class="panel-title">1. 上传 ZIP 并入库</el-text>
        <span class="muted-text">支持批量简历压缩包，自动写入任务与文本表</span>
      </div>
    </template>

    <el-space direction="vertical" class="full-width section-stack">
      <el-upload
        class="upload-area"
        drag
        :auto-upload="false"
        :show-file-list="true"
        :on-change="(f) => handleFileChange(f.raw!)"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽 zip 文件到此处，或<em>点击选择</em></div>
      </el-upload>
      <el-button type="success" size="large" :loading="loading" @click="submitUpload">提交上传</el-button>

      <el-divider />

      <el-descriptions v-if="latestResult" :column="1" border title="最近一次上传结果">
        <el-descriptions-item label="taskId">{{ latestResult.taskId }}</el-descriptions-item>
        <el-descriptions-item label="resumeCount">{{ latestResult.resumeCount }}</el-descriptions-item>
        <el-descriptions-item label="savedCount">{{ latestResult.savedCount }}</el-descriptions-item>
      </el-descriptions>
    </el-space>
  </el-card>
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

.upload-area {
  width: 100%;
}

.section-stack {
  gap: 14px;
}

.upload-icon {
  font-size: 34px;
  color: #6f87ff;
  margin-bottom: 8px;
}
</style>
