<script setup lang="ts">
import { ref, computed, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Loading, Search } from '@element-plus/icons-vue'
import TaskSelector from '@/components/TaskSelector.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import JsonViewer from '@/components/JsonViewer.vue'
import { uploadZip, queryMatchTask, submitMatchTask } from '@/api'
import type { UploadResponse, PythonTaskItem, PythonTaskResultPayload } from '@/api/types'

// 上传相关
const selectedFile = ref<File | null>(null)
const uploadLoading = ref(false)
const uploadResult = ref<UploadResponse | null>(null)

// 筛选相关
const taskId = ref('')
const jdText = ref('')
const topK = ref(20)
const recallK = ref(200)
const running = ref(false)
const lastResult = ref<PythonTaskResultPayload | null>(null)
let timer: number | null = null

const items = computed<PythonTaskItem[]>(() => lastResult.value?.result?.results?.items || [])
const taskStatus = computed(() => lastResult.value?.status || 'UNKNOWN')
const hasResult = computed(() => items.value.length > 0)

// 上传处理
function handleFileChange(file: File) {
  selectedFile.value = file
  return false
}

async function submitUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择 zip 文件')
    return
  }
  uploadLoading.value = true
  try {
    uploadResult.value = await uploadZip(selectedFile.value)
    ElMessage.success(`上传成功，taskId: ${uploadResult.value.taskId}`)
  } catch (error) {
    ElMessage.error((error as Error).message || '上传失败')
  } finally {
    uploadLoading.value = false
  }
}

// 筛选轮询
function stopPolling() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
  running.value = false
}

async function pollOnce() {
  if (!taskId.value) return
  try {
    const data = await queryMatchTask(taskId.value)
    lastResult.value = data
    if (data.status === 'done') {
      stopPolling()
      ElMessage.success('筛选任务已完成')
    } else if (data.status === 'failed') {
      stopPolling()
      ElMessage.error(data.error || '筛选任务失败')
    }
  } catch (error) {
    stopPolling()
    ElMessage.error((error as Error).message || '任务轮询失败')
  }
}

async function startScreen() {
  if (!taskId.value) {
    ElMessage.warning('请先选择 task')
    return
  }
  if (!jdText.value.trim()) {
    ElMessage.warning('请输入岗位 JD 文本')
    return
  }
  if (!Number.isFinite(topK.value) || topK.value < 1) {
    ElMessage.warning('topK 必须为正整数')
    return
  }
  if (!Number.isFinite(recallK.value) || recallK.value < 1) {
    ElMessage.warning('recallK 必须为正整数')
    return
  }
  try {
    await submitMatchTask({
      taskId: taskId.value,
      jdText: jdText.value.trim(),
      topK: Math.floor(topK.value),
      recallK: Math.floor(recallK.value),
    })
    ElMessage.success('提交成功，开始轮询任务进度')
    stopPolling()
    running.value = true
    await pollOnce()
    timer = window.setInterval(pollOnce, 2000)
  } catch (error) {
    ElMessage.error((error as Error).message || '提交筛选任务失败')
  }
}

onBeforeUnmount(stopPolling)
</script>

<template>
  <div class="page-container">
    <!-- 上部：上传与任务选择 -->
    <el-row :gutter="20" class="top-section">
      <!-- 左侧：上传 -->
      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card class="panel-card upload-card">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <el-icon class="header-icon"><UploadFilled /></el-icon>
                <span class="panel-title">上传简历</span>
              </div>
              <el-tag type="info" size="small">Step 1</el-tag>
            </div>
          </template>
          
          <div class="upload-section">
            <el-upload
              class="upload-area"
              drag
              :auto-upload="false"
              :show-file-list="true"
              :on-change="(f: { raw: File }) => handleFileChange(f.raw!)"
            >
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="upload-text">
                <div>拖拽 zip 文件到此处，或<em>点击选择</em></div>
                <div class="upload-hint">支持批量简历压缩包，自动写入任务与文本表</div>
              </div>
            </el-upload>
            
            <el-button 
              type="primary" 
              size="large" 
              :loading="uploadLoading" 
              @click="submitUpload"
              class="upload-btn"
            >
              提交上传
            </el-button>

            <!-- 上传结果 -->
            <div v-if="uploadResult" class="upload-result">
              <el-divider />
              <el-descriptions :column="1" border>
                <el-descriptions-item label="Task ID">
                  <el-tag type="success" effect="plain">{{ uploadResult.taskId }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="简历数量">{{ uploadResult.resumeCount }}</el-descriptions-item>
                <el-descriptions-item label="成功入库">{{ uploadResult.savedCount }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：选择任务 -->
      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card class="panel-card task-card">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <el-icon class="header-icon"><Search /></el-icon>
                <span class="panel-title">筛选任务</span>
              </div>
              <el-tag type="info" size="small">Step 2</el-tag>
            </div>
          </template>
          
          <div class="screen-section">
            <TaskSelector v-model="taskId" />
            
            <el-divider />
            
            <div class="jd-section">
              <div class="section-label">岗位描述 (JD)</div>
              <el-input
                v-model="jdText"
                type="textarea"
                :rows="6"
                placeholder="请输入岗位 JD 文本，用于简历匹配筛选..."
                class="jd-input"
              />

              <el-row :gutter="16" class="tuning-row">
                <el-col :span="12">
                  <div class="tuning-label">topK</div>
                  <el-input-number
                    v-model="topK"
                    :min="1"
                    :step="1"
                    class="tuning-input"
                  />
                </el-col>
                <el-col :span="12">
                  <div class="tuning-label">recallK</div>
                  <el-input-number
                    v-model="recallK"
                    :min="1"
                    :step="10"
                    class="tuning-input"
                  />
                </el-col>
              </el-row>
            </div>

            <div class="action-bar">
              <el-button 
                type="primary" 
                size="large" 
                :loading="running"
                @click="startScreen"
                class="screen-btn"
              >
                <el-icon v-if="!running"><Search /></el-icon>
                开始筛选
              </el-button>
              <StatusBadge :status="taskStatus" />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 下部：筛选结果 -->
    <el-card v-if="hasResult || running" class="panel-card result-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon"><Loading v-if="running" class="is-loading" /></el-icon>
            <span class="panel-title">筛选结果</span>
            <el-tag v-if="items.length > 0" type="success" effect="light" size="small">
              共 {{ items.length }} 条
            </el-tag>
          </div>
          <span class="muted-text">A+B 展示：结构化表格 + 原始 JSON</span>
        </div>
      </template>

      <el-tabs type="border-card" class="result-tabs">
        <el-tab-pane label="📊 结果表格">
          <el-table :data="items" stripe border style="width: 100%" class="result-table">
            <el-table-column type="index" width="60" align="center" />
            <el-table-column prop="resume_id" label="简历ID" min-width="180" show-overflow-tooltip />
            <el-table-column prop="file_name" label="文件名" min-width="180" show-overflow-tooltip />
            <el-table-column prop="final_score" label="综合得分" min-width="120" sortable>
              <template #default="{ row }">
                <el-progress 
                  :percentage="Math.round(row.final_score * 100)" 
                  :color="row.final_score > 0.7 ? '#67C23A' : row.final_score > 0.4 ? '#E6A23C' : '#F56C6C'"
                  :show-text="true"
                  :stroke-width="8"
                />
              </template>
            </el-table-column>
            <el-table-column prop="embedding_score" label="语义得分" min-width="100">
              <template #default="{ row }">
                <span :class="row.embedding_score > 0.7 ? 'score-high' : row.embedding_score > 0.4 ? 'score-mid' : 'score-low'">
                  {{ (row.embedding_score * 100).toFixed(1) }}%
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="recall_score" label="召回得分" min-width="100">
              <template #default="{ row }">
                <span :class="row.recall_score > 0.7 ? 'score-high' : row.recall_score > 0.4 ? 'score-mid' : 'score-low'">
                  {{ (row.recall_score * 100).toFixed(1) }}%
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="keyword_coverage" label="关键词覆盖" min-width="110">
              <template #default="{ row }">
                <span :class="row.keyword_coverage > 0.7 ? 'score-high' : row.keyword_coverage > 0.4 ? 'score-mid' : 'score-low'">
                  {{ (row.keyword_coverage * 100).toFixed(1) }}%
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="top_terms" label="匹配词" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.top_terms?.slice(0, 5).join(', ') }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="📄 原始 JSON">
          <JsonViewer :data="lastResult" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 空状态 -->
    <el-card v-else class="panel-card empty-card">
      <el-empty description="完成上传并执行筛选后，结果将在此展示">
        <template #image>
          <el-icon class="empty-icon"><Search /></el-icon>
        </template>
      </el-empty>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.top-section {
  margin-bottom: 0;
}

/* 卡片头部样式 */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  font-size: 20px;
  color: #5b8cff;
}

/* 上传区域 */
.upload-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upload-area {
  width: 100%;
}

.upload-area :deep(.el-upload-dragger) {
  height: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2px dashed #c0c4cc;
  border-radius: 12px;
  background: #fafbfc;
  transition: all 0.3s;
}

.upload-area :deep(.el-upload-dragger:hover) {
  border-color: #409eff;
  background: #f0f7ff;
}

.upload-icon {
  font-size: 48px;
  color: #5b8cff;
  margin-bottom: 16px;
}

.upload-text {
  text-align: center;
}

.upload-text em {
  color: #409eff;
  font-style: normal;
  font-weight: 500;
}

.upload-hint {
  font-size: 13px;
  color: #909399;
  margin-top: 8px;
}

.upload-btn {
  width: 100%;
}

.upload-result {
  margin-top: 8px;
}

/* 筛选区域 */
.screen-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.jd-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-label {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}

.jd-input :deep(textarea) {
  font-family: inherit;
  line-height: 1.6;
}

.tuning-row {
  margin-top: 6px;
}

.tuning-label {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
  margin-bottom: 6px;
}

.tuning-input :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.action-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 8px;
}

.screen-btn {
  min-width: 140px;
}

/* 结果区域 */
.result-card {
  flex: 1;
}

.result-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.result-table {
  margin-top: 8px;
}

.result-table :deep(th.el-table__cell) {
  background: #f2f6ff;
  font-weight: 600;
}

/* 分数颜色 */
.score-high {
  color: #67C23A;
  font-weight: 600;
}

.score-mid {
  color: #E6A23C;
  font-weight: 600;
}

.score-low {
  color: #F56C6C;
  font-weight: 600;
}

/* 空状态 */
.empty-card {
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-icon {
  font-size: 80px;
  color: #dcdfe6;
}

/* 响应式调整 */
@media (max-width: 768px) {
  .top-section :deep(.el-col) {
    margin-bottom: 20px;
  }
  
  .action-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
