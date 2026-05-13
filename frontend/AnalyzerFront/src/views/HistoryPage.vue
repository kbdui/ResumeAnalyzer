<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataAnalysis, Search } from '@element-plus/icons-vue'
import TaskSelector from '@/components/TaskSelector.vue'
import JsonViewer from '@/components/JsonViewer.vue'
import { deleteTask, listAnalysisByTask, listExtractedResumeByTask, listHybridResultsByTask, listTaskResumeByTask } from '@/api'
import type { AnalysisItem, ExtractedResumeItem, PythonTaskItem, PythonTaskResultPayload, TaskResumeItem } from '@/api/types'

const taskId = ref('')
const taskSelectorKey = ref(0)
const loading = ref(false)
const matchResult = ref<PythonTaskResultPayload | null>(null)
const analysisRows = ref<AnalysisItem[]>([])
const extractedResumeRows = ref<ExtractedResumeItem[]>([])
const taskResumeRows = ref<TaskResumeItem[]>([])

const pageSize = 10
const matchPage = ref(1)
const extractedPage = ref(1)
const hardFilterPage = ref(1)
const analysisPage = ref(1)

const resumeDetailDialogVisible = ref(false)
const hardFilterDetailDialogVisible = ref(false)
const matchDetailDialogVisible = ref(false)
const analysisDetailDialogVisible = ref(false)

const selectedResumeDetail = ref<ExtractedResumeItem | null>(null)
const selectedHardFilterDetail = ref<(TaskResumeItem & { parsed: Record<string, unknown> }) | null>(null)
const selectedMatchDetail = ref<PythonTaskItem | null>(null)
const selectedAnalysisDetail = ref<(AnalysisItem & { parsed: Record<string, unknown> }) | null>(null)

const matchItems = computed<PythonTaskItem[]>(() => matchResult.value?.result?.results?.items || [])

const parsedAnalysisRows = computed(() =>
  analysisRows.value.map((r) => {
    let parsed: Record<string, unknown> = {}
    try {
      parsed = JSON.parse(r.analysisJson) as Record<string, unknown>
    } catch {
      parsed = {}
    }
    return { ...r, parsed }
  }),
)

const parsedTaskResumeRows = computed(() =>
  taskResumeRows.value.map((r) => {
    let parsed: Record<string, unknown> = {}
    try {
      parsed = r.analysisJson ? (JSON.parse(r.analysisJson) as Record<string, unknown>) : {}
    } catch {
      parsed = {}
    }
    return { ...r, parsed }
  }),
)

const pagedMatchItems = computed(() => {
  const start = (matchPage.value - 1) * pageSize
  return matchItems.value.slice(start, start + pageSize)
})

const pagedExtractedResumeRows = computed(() => {
  const start = (extractedPage.value - 1) * pageSize
  return extractedResumeRows.value.slice(start, start + pageSize)
})

const pagedTaskResumeRows = computed(() => {
  const start = (hardFilterPage.value - 1) * pageSize
  return parsedTaskResumeRows.value.slice(start, start + pageSize)
})

const pagedAnalysisRows = computed(() => {
  const start = (analysisPage.value - 1) * pageSize
  return parsedAnalysisRows.value.slice(start, start + pageSize)
})

const analysisExtraRows = computed(() => {
  const parsed = (selectedAnalysisDetail.value?.parsed || {}) as Record<string, unknown>
  return Object.entries(parsed)
    .filter(([key]) => !['overall_level', 'overall_score', 'summary', 'strengths', 'risks', 'suggestions', 'job_match'].includes(key))
    .map(([key, value]) => ({ key, value }))
})

function formatScore(value?: number | null) {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return '-'
  }
  return value.toFixed(3)
}

function formatMaybeScore(value: unknown) {
  const n = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(n)) {
    return '-'
  }
  return n.toFixed(3)
}

function formatDisplayScore(row: { display_score?: number; final_score?: number | null }) {
  return formatScore(row.display_score ?? row.final_score)
}

function formatList(value: unknown) {
  if (Array.isArray(value)) {
    return value.length ? value.join('\n') : '-'
  }
  if (value === undefined || value === null || value === '') {
    return '-'
  }
  return String(value)
}

function formatJson(value: unknown) {
  if (value === undefined || value === null) {
    return '-'
  }
  if (typeof value === 'string') {
    return value
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function openResumeDetail(row: ExtractedResumeItem) {
  selectedResumeDetail.value = row
  resumeDetailDialogVisible.value = true
}

function openHardFilterDetail(row: TaskResumeItem & { parsed: Record<string, unknown> }) {
  selectedHardFilterDetail.value = row
  hardFilterDetailDialogVisible.value = true
}

function openMatchDetail(row: PythonTaskItem) {
  selectedMatchDetail.value = row
  matchDetailDialogVisible.value = true
}

function openAnalysisDetail(row: AnalysisItem & { parsed: Record<string, unknown> }) {
  selectedAnalysisDetail.value = row
  analysisDetailDialogVisible.value = true
}

function getDimValue(key: string, field: string) {
  const parsed = selectedHardFilterDetail.value?.parsed as Record<string, any> | undefined
  const dim = parsed?.[key] as Record<string, any> | undefined
  return dim?.[field]
}

function getSelectedAnalysisValue(field: string) {
  const parsed = selectedAnalysisDetail.value?.parsed as Record<string, unknown> | undefined
  return parsed?.[field]
}

function resetHistoryState() {
  taskId.value = ''
  matchResult.value = null
  analysisRows.value = []
  extractedResumeRows.value = []
  taskResumeRows.value = []
  selectedResumeDetail.value = null
  selectedHardFilterDetail.value = null
  selectedMatchDetail.value = null
  selectedAnalysisDetail.value = null
  resumeDetailDialogVisible.value = false
  hardFilterDetailDialogVisible.value = false
  matchDetailDialogVisible.value = false
  analysisDetailDialogVisible.value = false
  matchPage.value = 1
  extractedPage.value = 1
  hardFilterPage.value = 1
  analysisPage.value = 1
}

function parseStoredMatchResult(resultJson?: string | null) {
  if (!resultJson?.trim()) {
    return null
  }
  return JSON.parse(resultJson) as PythonTaskResultPayload
}

async function queryHistory() {
  if (!taskId.value) {
    ElMessage.warning('请先选择 task')
    return
  }
  loading.value = true
  const errors: string[] = []

  try {
    const hybridRows = await listHybridResultsByTask(taskId.value)
    const latestRow = hybridRows.find((row) => row.resultJson && row.resultJson.trim())
    matchResult.value = latestRow ? parseStoredMatchResult(latestRow.resultJson) : null
  } catch (error) {
    matchResult.value = null
    errors.push(`召回筛选结果查询失败：${(error as Error).message}`)
  }

  try {
    analysisRows.value = await listAnalysisByTask(taskId.value)
  } catch (error) {
    analysisRows.value = []
    errors.push(`大模型评估结果查询失败：${(error as Error).message}`)
  }

  try {
    extractedResumeRows.value = await listExtractedResumeByTask(taskId.value)
  } catch (error) {
    extractedResumeRows.value = []
    errors.push(`结构化简历信息查询失败：${(error as Error).message}`)
  }

  try {
    taskResumeRows.value = await listTaskResumeByTask(taskId.value)
  } catch (error) {
    taskResumeRows.value = []
    errors.push(`硬过滤结果查询失败：${(error as Error).message}`)
  }

  matchPage.value = 1
  extractedPage.value = 1
  hardFilterPage.value = 1
  analysisPage.value = 1
  loading.value = false

  if (errors.length > 0) {
    ElMessage.warning(errors[0]!)
  } else {
    ElMessage.success('历史记录查询完成')
  }
}

async function removeCurrentTask() {
  if (!taskId.value) {
    ElMessage.warning('请先选择 task')
    return
  }

  try {
    await ElMessageBox.confirm(`确认删除任务 ${taskId.value} 及其关联数据吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch (error) {
    const action = error as string
    if (action === 'cancel' || action === 'close') {
      return
    }
    ElMessage.error((error as Error).message || '删除确认失败')
    return
  }

  loading.value = true
  const currentTaskId = taskId.value
  try {
    await deleteTask(currentTaskId)
    resetHistoryState()
    taskSelectorKey.value += 1
    ElMessage.success('任务已删除')
  } catch (error) {
    ElMessage.error((error as Error).message || '删除失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="history-page">
    <el-card class="panel-card">
      <template #header>
        <div class="header-left">
          <el-icon><DataAnalysis /></el-icon>
          <span>历史记录查询</span>
        </div>
      </template>
      <TaskSelector :key="taskSelectorKey" v-model="taskId" />
      <el-space class="mt12" wrap>
        <el-button type="primary" :loading="loading" @click="queryHistory">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button type="danger" plain :loading="loading" :disabled="!taskId" @click="removeCurrentTask">
          删除任务
        </el-button>
      </el-space>
    </el-card>

    <el-card class="panel-card">
      <template #header><span>结构化简历信息（resume）</span></template>
      <el-table :data="pagedExtractedResumeRows" stripe max-height="360">
        <el-table-column type="index" width="60" />
        <el-table-column prop="resume_id" label="简历ID" min-width="180" />
        <el-table-column label="姓名" width="140">
          <template #default="{ row }">{{ row.personal_info?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="联系方式" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.personal_info?.contact || row.personal_info?.email || '-' }}</template>
        </el-table-column>
        <el-table-column label="技能数" width="100">
          <template #default="{ row }">{{ row.skills?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="工作经历数" width="110">
          <template #default="{ row }">{{ row.work_experience?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="详情" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="openResumeDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="prev, pager, next, total"
        :page-size="pageSize"
        :total="extractedResumeRows.length"
        :current-page="extractedPage"
        @current-change="(v: number) => (extractedPage = v)"
      />
    </el-card>

    <el-card class="panel-card">
      <template #header><span>硬过滤结果（task_resume）</span></template>
      <el-table :data="pagedTaskResumeRows" stripe max-height="360">
        <el-table-column type="index" width="60" />
        <el-table-column prop="resumeId" label="简历ID" min-width="180" />
        <el-table-column label="是否通过" width="120">
          <template #default="{ row }">
            <el-tag :type="row.pass ? 'success' : 'danger'" effect="light">{{ row.pass ? 'PASS' : 'FAIL' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="教育" width="120">
          <template #default="{ row }">{{ row.parsed.education?.status || '-' }}</template>
        </el-table-column>
        <el-table-column label="工作经验" width="120">
          <template #default="{ row }">{{ row.parsed.work_experience?.status || '-' }}</template>
        </el-table-column>
        <el-table-column label="技能" width="100">
          <template #default="{ row }">{{ row.parsed.skills?.status || '-' }}</template>
        </el-table-column>
        <el-table-column label="项目" width="100">
          <template #default="{ row }">{{ row.parsed.projects?.status || '-' }}</template>
        </el-table-column>
        <el-table-column label="详情" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="openHardFilterDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="prev, pager, next, total"
        :page-size="pageSize"
        :total="parsedTaskResumeRows.length"
        :current-page="hardFilterPage"
        @current-change="(v: number) => (hardFilterPage = v)"
      />
    </el-card>

    <el-card class="panel-card">
      <template #header><span>召回筛选结果</span></template>
      <el-table :data="pagedMatchItems" stripe max-height="360">
        <el-table-column type="index" width="60" />
        <el-table-column prop="resume_id" label="简历ID" min-width="180" />
        <el-table-column prop="file_name" label="文件名" min-width="180" />
        <el-table-column prop="final_score" label="综合分" width="120">
          <template #default="{ row }">{{ formatDisplayScore(row) }}</template>
        </el-table-column>
        <el-table-column label="详情" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="openMatchDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="prev, pager, next, total"
        :page-size="pageSize"
        :total="matchItems.length"
        :current-page="matchPage"
        @current-change="(v: number) => (matchPage = v)"
      />
    </el-card>

    <el-card class="panel-card">
      <template #header><span>大模型评估结果</span></template>
      <el-table :data="pagedAnalysisRows" stripe max-height="360">
        <el-table-column prop="resumeId" label="简历ID" min-width="180" />
        <el-table-column label="等级" width="160">
          <template #default="{ row }">{{ row.parsed.overall_level || '-' }}</template>
        </el-table-column>
        <el-table-column label="分数" width="120">
          <template #default="{ row }">{{ formatMaybeScore(row.parsed.overall_score) }}</template>
        </el-table-column>
        <el-table-column label="总结" min-width="420" show-overflow-tooltip>
          <template #default="{ row }">{{ row.parsed.summary || '-' }}</template>
        </el-table-column>
        <el-table-column label="详情" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="openAnalysisDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        background
        layout="prev, pager, next, total"
        :page-size="pageSize"
        :total="parsedAnalysisRows.length"
        :current-page="analysisPage"
        @current-change="(v: number) => (analysisPage = v)"
      />
    </el-card>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card class="panel-card"><template #header><span>召回结果 JSON</span></template><JsonViewer :data="matchResult" /></el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="panel-card"><template #header><span>结构化简历 JSON</span></template><JsonViewer :data="extractedResumeRows" /></el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card class="panel-card"><template #header><span>硬过滤 JSON</span></template><JsonViewer :data="taskResumeRows" /></el-card>
      </el-col>
      <el-col :span="12">
        <el-card class="panel-card"><template #header><span>评估结果 JSON</span></template><JsonViewer :data="analysisRows" /></el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="resumeDetailDialogVisible" title="结构化简历详情" width="760px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="简历ID">{{ selectedResumeDetail?.resume_id || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ selectedResumeDetail?.personal_info?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="联系方式">
          {{ selectedResumeDetail?.personal_info?.contact || selectedResumeDetail?.personal_info?.email || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="技能">{{ selectedResumeDetail?.skills?.join('、') || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-divider>工作经历</el-divider>
      <el-timeline v-if="selectedResumeDetail?.work_experience?.length">
        <el-timeline-item
          v-for="(work, idx) in selectedResumeDetail.work_experience"
          :key="idx"
          :timestamp="work.duration || '-'"
        >
          <div><strong>{{ work.company || '-' }}</strong> / {{ work.position || '-' }}</div>
          <div class="muted">{{ work.description || '-' }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无工作经历" />
    </el-dialog>

    <el-dialog v-model="hardFilterDetailDialogVisible" title="硬过滤分析详情" width="780px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="简历ID">{{ selectedHardFilterDetail?.resumeId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="是否通过">
          <el-tag :type="selectedHardFilterDetail?.pass ? 'success' : 'danger'">
            {{ selectedHardFilterDetail?.pass ? 'PASS' : 'FAIL' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <el-divider />
      <el-table
        :data="[
          { key: 'education', label: '教育背景' },
          { key: 'work_experience', label: '工作经验' },
          { key: 'skills', label: '技能' },
          { key: 'projects', label: '项目经验' },
        ]"
        border
      >
        <el-table-column prop="label" label="维度" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ getDimValue(row.key, 'status') || '-' }}</template>
        </el-table-column>
        <el-table-column label="置信度" width="100">
          <template #default="{ row }">{{ formatMaybeScore(getDimValue(row.key, 'confidence')) }}</template>
        </el-table-column>
        <el-table-column label="分析理由" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">{{ getDimValue(row.key, 'reason') || '-' }}</template>
        </el-table-column>
        <el-table-column label="证据" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ formatList(getDimValue(row.key, 'evidence')) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="matchDetailDialogVisible" title="召回筛选详情" width="980px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="简历ID">{{ selectedMatchDetail?.resume_id || '-' }}</el-descriptions-item>
        <el-descriptions-item label="文件名">{{ selectedMatchDetail?.file_name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="综合分">{{ formatDisplayScore(selectedMatchDetail || {}) }}</el-descriptions-item>
        <el-descriptions-item label="原始总分">{{ formatMaybeScore(selectedMatchDetail?.raw_final_score) }}</el-descriptions-item>
        <el-descriptions-item label="召回分">{{ formatMaybeScore(selectedMatchDetail?.recall_score) }}</el-descriptions-item>
        <el-descriptions-item label="Embedding分">{{ formatMaybeScore(selectedMatchDetail?.embedding_score) }}</el-descriptions-item>
        <el-descriptions-item label="TF-IDF分">{{ formatMaybeScore(selectedMatchDetail?.tfidf_score) }}</el-descriptions-item>
        <el-descriptions-item label="关键词覆盖">{{ formatMaybeScore(selectedMatchDetail?.keyword_coverage) }}</el-descriptions-item>
        <el-descriptions-item label="工作经验分">{{ formatMaybeScore(selectedMatchDetail?.work_experience_score) }}</el-descriptions-item>
        <el-descriptions-item label="项目分">{{ formatMaybeScore(selectedMatchDetail?.project_score) }}</el-descriptions-item>
        <el-descriptions-item label="技能分">{{ formatMaybeScore(selectedMatchDetail?.skills_score) }}</el-descriptions-item>
        <el-descriptions-item label="教育分">{{ formatMaybeScore(selectedMatchDetail?.education_score) }}</el-descriptions-item>
        <el-descriptions-item label="摘要分">{{ formatMaybeScore(selectedMatchDetail?.summary_score) }}</el-descriptions-item>
        <el-descriptions-item label="经历分">{{ formatMaybeScore(selectedMatchDetail?.experience_score) }}</el-descriptions-item>
        <el-descriptions-item label="角色对齐分">{{ formatMaybeScore(selectedMatchDetail?.role_alignment_score) }}</el-descriptions-item>
        <el-descriptions-item label="负向惩罚">{{ formatMaybeScore(selectedMatchDetail?.negative_penalty) }}</el-descriptions-item>
        <el-descriptions-item label="Top Terms" :span="2">{{ formatList(selectedMatchDetail?.top_terms) }}</el-descriptions-item>
        <el-descriptions-item label="角色对齐理由" :span="2">{{ formatList(selectedMatchDetail?.role_alignment_reasons) }}</el-descriptions-item>
        <el-descriptions-item label="经历判断理由" :span="2">{{ formatList(selectedMatchDetail?.experience_reasons) }}</el-descriptions-item>
        <el-descriptions-item label="惩罚理由" :span="2">{{ formatList(selectedMatchDetail?.penalty_reasons) }}</el-descriptions-item>
      </el-descriptions>

      <el-divider>分段信息</el-divider>
      <el-table
        :data="[
          { key: 'summary', label: '简历摘要' },
          { key: 'work', label: '工作经验' },
          { key: 'project', label: '项目经验' },
          { key: 'skills', label: '技能' },
          { key: 'education', label: '教育' },
          { key: 'work_project', label: '工作+项目' },
        ]"
        border
      >
        <el-table-column prop="label" label="维度" width="140" />
        <el-table-column label="内容" min-width="700">
          <template #default="{ row }">
            <div class="detail-pre">{{ formatJson((selectedMatchDetail?.segments as Record<string, unknown> | undefined)?.[row.key]) }}</div>
          </template>
        </el-table-column>
      </el-table>

      <el-divider>原始 JSON</el-divider>
      <JsonViewer :data="selectedMatchDetail" />
    </el-dialog>

    <el-dialog v-model="analysisDetailDialogVisible" title="大模型评估详情" width="980px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="简历ID">{{ selectedAnalysisDetail?.resumeId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ formatList(getSelectedAnalysisValue('overall_level')) }}</el-descriptions-item>
        <el-descriptions-item label="评分">{{ formatMaybeScore(getSelectedAnalysisValue('overall_score')) }}</el-descriptions-item>
        <el-descriptions-item label="岗位匹配">{{ formatList(getSelectedAnalysisValue('job_match')) }}</el-descriptions-item>
        <el-descriptions-item label="总结" :span="2">{{ formatList(getSelectedAnalysisValue('summary')) }}</el-descriptions-item>
        <el-descriptions-item label="优势" :span="2">{{ formatList(getSelectedAnalysisValue('strengths')) }}</el-descriptions-item>
        <el-descriptions-item label="风险/不足" :span="2">{{ formatList(getSelectedAnalysisValue('risks')) }}</el-descriptions-item>
        <el-descriptions-item label="建议" :span="2">{{ formatList(getSelectedAnalysisValue('suggestions')) }}</el-descriptions-item>
      </el-descriptions>

      <template v-if="analysisExtraRows.length">
        <el-divider>评估明细</el-divider>
        <el-table :data="analysisExtraRows" border>
          <el-table-column prop="key" label="字段" width="220" />
          <el-table-column label="内容" min-width="680">
            <template #default="{ row }">
              <div class="detail-pre">{{ formatJson(row.value) }}</div>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <el-divider>原始 JSON</el-divider>
      <JsonViewer :data="selectedAnalysisDetail?.parsed || selectedAnalysisDetail" />
    </el-dialog>
  </div>
</template>

<style scoped>
.history-page { display: flex; flex-direction: column; gap: 20px; }
.header-left { display: flex; align-items: center; gap: 8px; }
.mt12 { margin-top: 12px; }
.pager { margin-top: 12px; justify-content: flex-end; }
.muted { color: #606266; margin-top: 4px; }
.detail-pre { white-space: pre-wrap; word-break: break-word; line-height: 1.6; }
</style>
