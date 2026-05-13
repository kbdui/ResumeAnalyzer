<script setup lang="ts">
import { ElMessage } from 'element-plus'
import JsonViewer from '@/components/JsonViewer.vue'
import WorkflowUploadCard from '@/components/workflow/WorkflowUploadCard.vue'
import WorkflowContextCard from '@/components/workflow/WorkflowContextCard.vue'
import StepExtractPanel from '@/components/workflow/StepExtractPanel.vue'
import StepHardFilterPanel from '@/components/workflow/StepHardFilterPanel.vue'
import StepHybridPanel from '@/components/workflow/StepHybridPanel.vue'
import StepAnalyzePanel from '@/components/workflow/StepAnalyzePanel.vue'
import { useWorkflowRunner } from '@/composables/useWorkflowRunner'

const {
  uploadLoading,
  uploadResult,
  taskId,
  jdText,
  topK,
  recallK,
  extractBatchSize,
  hardFilterBatchSize,
  analyzeBatchSize,
  analyzeCount,
  runningPipeline,
  runningExtract,
  runningHardFilter,
  runningHybrid,
  runningAnalyze,
  runningAnyStep,
  extractTaskId,
  hardFilterTaskId,
  analyzeTaskId,
  extractStatus,
  hardFilterStatus,
  analyzeStatus,
  matchResult,
  analysisRows,
  matchItems,
  taskResumePassCount,
  recallSelectedCount,
  hasResult,
  parsedAnalysisRows,
  submitUpload,
  runExtractStep,
  runHardFilterStep,
  runHybridStep,
  runAnalyzeStep,
  runFullPipeline,
  refreshFinalOutputs,
} = useWorkflowRunner()

function setTaskId(value: string) {
  taskId.value = value
}

function setJdText(value: string) {
  jdText.value = value
}

function setTopK(value: number) {
  topK.value = value
}

function setRecallK(value: number) {
  recallK.value = value
}

function setExtractBatchSize(value: number) {
  extractBatchSize.value = value
}

function setHardFilterBatchSize(value: number) {
  hardFilterBatchSize.value = value
}

function setAnalyzeBatchSize(value: number) {
  analyzeBatchSize.value = value
}

function setAnalyzeCount(value: number) {
  analyzeCount.value = value
}

async function onUpload(file: File) {
  try {
    const result = await submitUpload(file)
    ElMessage.success(`上传成功，taskId: ${result.taskId}`)
  } catch (error) {
    ElMessage.error((error as Error).message || '上传失败')
  }
}

async function onRunAll() {
  try {
    await runFullPipeline()
    ElMessage.success('全流程已完成')
  } catch (error) {
    ElMessage.error((error as Error).message || '全流程执行失败')
  }
}

async function refreshResultsOnly() {
  try {
    await refreshFinalOutputs()
    ElMessage.success('结果已刷新')
  } catch (error) {
    ElMessage.error((error as Error).message || '刷新失败')
  }
}

async function runStep(handler: () => Promise<void>, successMsg: string) {
  try {
    await handler()
    ElMessage.success(successMsg)
  } catch (error) {
    ElMessage.error((error as Error).message || '执行失败')
  }
}

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
</script>

<template>
  <div class="page-container">
    <el-row :gutter="16" class="top-row">
      <el-col :xs="24" :md="12">
        <WorkflowUploadCard :loading="uploadLoading" :upload-result="uploadResult" @submit="onUpload" />
      </el-col>
      <el-col :xs="24" :md="12">
        <WorkflowContextCard
          :task-id="taskId"
          :jd-text="jdText"
          :running-pipeline="runningPipeline"
          :running-any-step="runningAnyStep"
          @update:task-id="setTaskId"
          @update:jd-text="setJdText"
          @run-all="onRunAll"
          @refresh="refreshResultsOnly"
        />
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <StepExtractPanel
          :current-task-id="taskId"
          :batch-size="extractBatchSize"
          :running="runningExtract"
          :action-disabled="runningAnyStep"
          :task-ref-id="extractTaskId"
          :status-data="extractStatus"
          @update:batch-size="setExtractBatchSize"
          @run="runStep(runExtractStep, '提取完成')"
        />
      </el-col>
      <el-col :xs="24" :md="12">
        <StepHardFilterPanel
          :current-task-id="taskId"
          :batch-size="hardFilterBatchSize"
          :running="runningHardFilter"
          :action-disabled="runningAnyStep"
          :task-ref-id="hardFilterTaskId"
          :status-data="hardFilterStatus"
          @update:batch-size="setHardFilterBatchSize"
          @run="runStep(runHardFilterStep, '硬过滤完成')"
        />
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <StepHybridPanel
          :current-task-id="taskId"
          :top-k="topK"
          :recall-k="recallK"
          :hard-filter-passed-count="taskResumePassCount"
          :running="runningHybrid"
          :action-disabled="runningAnyStep"
          :result-data="matchResult"
          @update:top-k="setTopK"
          @update:recall-k="setRecallK"
          @run="runStep(runHybridStep, '召回筛选完成')"
        />
      </el-col>
      <el-col :xs="24" :md="12">
        <StepAnalyzePanel
          :current-task-id="taskId"
          :batch-size="analyzeBatchSize"
          :analyze-count="analyzeCount"
          :recall-selected-count="recallSelectedCount"
          :running="runningAnalyze"
          :action-disabled="runningAnyStep"
          :task-ref-id="analyzeTaskId"
          :status-data="analyzeStatus"
          @update:batch-size="setAnalyzeBatchSize"
          @update:analyze-count="setAnalyzeCount"
          @run="runStep(runAnalyzeStep, '大模型评估完成')"
        />
      </el-col>
    </el-row>

    <el-card v-if="hasResult" class="panel-card">
      <template #header><span class="panel-title">召回筛选结果（Step 4）</span></template>
      <el-table :data="matchItems" stripe max-height="360">
        <el-table-column type="index" width="60" />
        <el-table-column prop="resume_id" label="简历ID" min-width="180" />
        <el-table-column prop="file_name" label="文件名" min-width="180" />
        <el-table-column prop="final_score" label="综合分" width="120">
          <template #default="{ row }">{{ formatDisplayScore(row) }}</template>
        </el-table-column>
        <el-table-column prop="work_experience_score" label="工作经验分" width="120">
          <template #default="{ row }">{{ formatScore(row.work_experience_score) }}</template>
        </el-table-column>
        <el-table-column prop="skills_score" label="技能分" width="100">
          <template #default="{ row }">{{ formatScore(row.skills_score) }}</template>
        </el-table-column>
        <el-table-column prop="education_score" label="教育分" width="100">
          <template #default="{ row }">{{ formatScore(row.education_score) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="hasResult" class="panel-card">
      <template #header><span class="panel-title">大模型评估结果（Step 5）</span></template>
      <el-table :data="parsedAnalysisRows" stripe max-height="420">
        <el-table-column type="index" width="60" />
        <el-table-column prop="resumeId" label="简历ID" min-width="180" />
        <el-table-column label="等级" width="160">
          <template #default="{ row }">{{ row.parsed.overall_level || '-' }}</template>
        </el-table-column>
        <el-table-column label="评分" width="120">
          <template #default="{ row }">{{ formatMaybeScore(row.parsed.overall_score) }}</template>
        </el-table-column>
        <el-table-column label="总结" min-width="360" show-overflow-tooltip>
          <template #default="{ row }">{{ row.parsed.summary || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="hasResult" class="panel-card">
      <template #header><span class="panel-title">原始 JSON</span></template>
      <el-row :gutter="12">
        <el-col :span="12"><JsonViewer :data="matchResult" /></el-col>
        <el-col :span="12"><JsonViewer :data="analysisRows" /></el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style scoped>
.page-container { display: flex; flex-direction: column; gap: 20px; }
.top-row { align-items: stretch; }
.top-row :deep(.el-col) { display: flex; }
.top-row :deep(.panel-card) { width: 100%; height: 100%; }
</style>
