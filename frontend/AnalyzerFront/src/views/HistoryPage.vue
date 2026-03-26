<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { DataAnalysis, Search, MagicStick, Document } from '@element-plus/icons-vue'
import TaskSelector from '@/components/TaskSelector.vue'
import JsonViewer from '@/components/JsonViewer.vue'
import {
  listAnalyzeTasksByTask,
  listResumeByTask,
  listResumeByTaskAndAnalyzeTask,
  queryMatchTask,
} from '@/api'
import type { PythonTaskItem, PythonTaskResultPayload, TaskResumeMainView } from '@/api/types'

const taskId = ref('')
const analyzeTaskId = ref('')
const analyzeTaskOptions = ref<string[]>([])
const loading = ref(false)

const screenResult = ref<PythonTaskResultPayload | null>(null)
const resumeRows = ref<TaskResumeMainView[]>([])

const screenItems = computed<PythonTaskItem[]>(() => screenResult.value?.result?.results?.items || [])
const hasAnyResult = computed(() => !!screenResult.value || resumeRows.value.length > 0)

function formatNumber(value?: number | null, digits = 4) {
  if (value === undefined || value === null || Number.isNaN(value)) return '-'
  return value.toFixed(digits)
}

function formatDateTime(input?: string) {
  if (!input) return '-'
  const date = new Date(input)
  if (Number.isNaN(date.getTime())) return input
  return date.toLocaleString('zh-CN', { hour12: false })
}

/**
 * 分析任务 ID 列表依赖当前选中的 task，在 task 变更时拉取。
 */
async function refreshAnalyzeTaskOptions() {
  if (!taskId.value) {
    analyzeTaskOptions.value = []
    return
  }
  try {
    analyzeTaskOptions.value = await listAnalyzeTasksByTask(taskId.value)
  } catch {
    analyzeTaskOptions.value = []
    return
  }
  const selected = analyzeTaskId.value.trim()
  if (analyzeTaskOptions.value.length > 0) {
    if (!selected || !analyzeTaskOptions.value.includes(selected)) {
      analyzeTaskId.value = analyzeTaskOptions.value[0]!
    }
  }
}

watch(taskId, async (id) => {
  if (!id) {
    analyzeTaskOptions.value = []
    analyzeTaskId.value = ''
    return
  }
  await refreshAnalyzeTaskOptions()
})

async function queryHistory() {
  if (!taskId.value) {
    ElMessage.warning('请先选择 task')
    return
  }

  loading.value = true
  const errors: string[] = []

  try {
    screenResult.value = await queryMatchTask(taskId.value)
  } catch (error) {
    screenResult.value = null
    errors.push(`筛选结果查询失败：${(error as Error).message || '未知错误'}`)
  }

  try {
    if (analyzeTaskId.value.trim()) {
      resumeRows.value = await listResumeByTaskAndAnalyzeTask(taskId.value, analyzeTaskId.value.trim())
    } else {
      // 兼容：如果没有 analyze_task_id 数据，则退回展示所有入库结果（相当于历史混合）
      resumeRows.value = await listResumeByTask(taskId.value)
    }
  } catch (error) {
    resumeRows.value = []
    errors.push(`深度分析结果查询失败：${(error as Error).message || '未知错误'}`)
  }

  loading.value = false

  if (errors.length) {
    ElMessage.warning(errors[0])
  } else {
    ElMessage.success('历史记录查询完成')
  }
}
</script>

<template>
  <div class="history-page">
    <el-row :gutter="20" class="control-section">
      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card class="panel-card action-card">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <el-icon class="header-icon"><DataAnalysis /></el-icon>
                <span class="panel-title">历史记录查询</span>
              </div>
              <el-tag type="info" effect="light" size="small">任务追踪</el-tag>
            </div>
          </template>

          <TaskSelector v-model="taskId" />
          <el-divider />

          <div class="optional-row">
            <div class="muted-text">可选：分析任务ID（用于查询深度分析任务状态）</div>
            <el-select
              v-model="analyzeTaskId"
              placeholder="请选择分析任务ID（可不选，默认最近一次）"
              clearable
              filterable
            >
              <el-option
                v-for="id in analyzeTaskOptions"
                :key="id"
                :label="id"
                :value="id"
              />
            </el-select>
          </div>

          <el-button class="query-btn" type="primary" size="large" :loading="loading" @click="queryHistory">
            <el-icon><Search /></el-icon>
            查询历史记录
          </el-button>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="24" :md="12" :lg="12">
        <el-card class="panel-card summary-card">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <el-icon class="header-icon"><Document /></el-icon>
                <span class="panel-title">查询摘要</span>
              </div>
            </div>
          </template>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="Task ID">
              <el-tag v-if="taskId" type="primary" effect="plain">{{ taskId }}</el-tag>
              <span v-else class="muted-text">未选择</span>
            </el-descriptions-item>
            <el-descriptions-item label="分析任务 ID">
              <el-tag v-if="analyzeTaskId" type="info" effect="plain">{{ analyzeTaskId }}</el-tag>
              <span v-else class="muted-text">未选择</span>
            </el-descriptions-item>
            <el-descriptions-item label="筛选命中数">
              {{ screenItems.length }}
            </el-descriptions-item>
            <el-descriptions-item label="深度分析入库数">
              {{ resumeRows.length }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <template v-if="hasAnyResult">
      <el-row :gutter="20" class="result-section">
        <el-col :xs="24" :sm="24" :md="24" :lg="12">
          <el-card class="panel-card">
            <template #header>
              <div class="card-header">
                <div class="header-left">
                  <el-icon class="header-icon"><Search /></el-icon>
                  <span class="panel-title">简历筛选历史结果</span>
                </div>
                <el-tag type="success" effect="light" size="small">{{ screenItems.length }} 条</el-tag>
              </div>
            </template>

            <el-table :data="screenItems" stripe max-height="480">
              <el-table-column type="index" label="#" width="60" />
              <el-table-column prop="file_name" label="文件名" min-width="190" show-overflow-tooltip />
              <el-table-column prop="final_score" label="总分" width="96">
                <template #default="{ row }">{{ formatNumber(row.final_score) }}</template>
              </el-table-column>
              <el-table-column prop="recall_score" label="召回分" width="96">
                <template #default="{ row }">{{ formatNumber(row.recall_score) }}</template>
              </el-table-column>
              <el-table-column prop="embedding_score" label="向量分" width="96">
                <template #default="{ row }">{{ formatNumber(row.embedding_score) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="24" :md="24" :lg="12">
          <el-card class="panel-card">
            <template #header>
              <div class="card-header">
                <div class="header-left">
                  <el-icon class="header-icon"><MagicStick /></el-icon>
                  <span class="panel-title">深度分析历史结果</span>
                </div>
                <el-tag type="primary" effect="light" size="small">{{ resumeRows.length }} 条</el-tag>
              </div>
            </template>

            <el-table :data="resumeRows" stripe max-height="480">
              <el-table-column prop="rankNo" label="排名" width="80" />
              <el-table-column label="姓名" min-width="110">
                <template #default="{ row }">
                  {{ row.resume?.personalInfo?.name || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="联系方式" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.resume?.personalInfo?.contact || row.resume?.personalInfo?.email || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="finalScore" label="匹配分" width="96">
                <template #default="{ row }">{{ formatNumber(row.finalScore) }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="分析时间" width="180">
                <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" class="json-section">
        <el-col :xs="24" :sm="24" :md="12" :lg="12">
          <el-card class="panel-card">
            <template #header>
              <div class="card-header">
                <span class="panel-title">筛选结果 JSON</span>
              </div>
            </template>
            <JsonViewer :data="screenResult" />
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="24" :md="12" :lg="12">
          <el-card class="panel-card">
            <template #header>
              <div class="card-header">
                <span class="panel-title">深度分析结果 JSON</span>
              </div>
            </template>
            <JsonViewer :data="resumeRows" />
          </el-card>
        </el-col>
      </el-row>
    </template>

    <el-card v-else class="panel-card empty-card">
      <el-empty description="请输入 task 并查询历史记录" />
    </el-card>
  </div>
</template>

<style scoped>
.history-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.control-section {
  margin-bottom: 2px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-icon {
  font-size: 20px;
  color: #5b8cff;
}

.optional-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.query-btn {
  margin-top: 16px;
  width: 100%;
}

.result-section,
.json-section {
  margin-top: 2px;
}

.empty-card {
  padding-top: 8px;
}
</style>

