import { computed, ref, type Ref } from 'vue'
import {
  listAnalysisByTask,
  queryExtractTask,
  queryAnalyzeTask,
  queryHardFilterTask,
  queryMatchTask,
  submitAnalyzeTaskWithBatch,
  submitExtractTask,
  submitHardFilterTask,
  submitMatchTask,
  uploadZip,
} from '@/api'
import type { AnalyzeTaskStatus, AnalysisItem, PythonTaskItem, PythonTaskResultPayload, UploadResponse } from '@/api/types'

export function useWorkflowRunner() {
  const uploadLoading = ref(false)
  const uploadResult = ref<UploadResponse | null>(null)

  const taskId = ref('')
  const jdText = ref('')
  const topK = ref(20)
  const recallK = ref(200)
  const extractBatchSize = ref(5)
  const analyzeBatchSize = ref(5)
  const runningPipeline = ref(false)
  const runningExtract = ref(false)
  const runningHardFilter = ref(false)
  const runningHybrid = ref(false)
  const runningAnalyze = ref(false)

  const extractTaskId = ref('')
  const hardFilterTaskId = ref('')
  const analyzeTaskId = ref('')

  const extractStatus = ref<AnalyzeTaskStatus | null>(null)
  const hardFilterStatus = ref<AnalyzeTaskStatus | null>(null)
  const analyzeStatus = ref<AnalyzeTaskStatus | null>(null)
  const matchResult = ref<PythonTaskResultPayload | null>(null)
  const analysisRows = ref<AnalysisItem[]>([])

  const matchItems = computed<PythonTaskItem[]>(() => matchResult.value?.result?.results?.items || [])
  const hasResult = computed(() => matchItems.value.length > 0 || analysisRows.value.length > 0)
  const parsedAnalysisRows = computed(() =>
    analysisRows.value.map((row) => {
      let parsed: Record<string, unknown> = {}
      try {
        parsed = JSON.parse(row.analysisJson) as Record<string, unknown>
      } catch {
        parsed = {}
      }
      return { ...row, parsed }
    }),
  )

  function ensureRunParams() {
    if (!taskId.value) throw new Error('请先选择 task')
    if (!jdText.value.trim()) throw new Error('请输入岗位 JD 文本')
    if (!Number.isFinite(topK.value) || topK.value < 1) throw new Error('topK 必须为正整数')
    if (!Number.isFinite(recallK.value) || recallK.value < 1) throw new Error('recallK 必须为正整数')
  }

  function sleep(ms: number) {
    return new Promise((resolve) => window.setTimeout(resolve, ms))
  }

  async function pollTaskStatus(
    taskRef: Ref<string>,
    statusRef: Ref<AnalyzeTaskStatus | null>,
    queryFn: (taskId: string) => Promise<AnalyzeTaskStatus>,
  ) {
    if (!taskRef.value) return
    while (true) {
      const status = await queryFn(taskRef.value)
      statusRef.value = status
      if (status.status === 'SUCCESS' || status.status === 'PARTIAL_SUCCESS') return
      if (status.status === 'FAILED') throw new Error(status.error || '任务执行失败')
      await sleep(2000)
    }
  }

  async function pollMatchDone() {
    while (true) {
      const result = await queryMatchTask(taskId.value)
      matchResult.value = result
      if (result.status === 'done') return
      if (result.status === 'failed') throw new Error(result.error || '召回筛选失败')
      await sleep(2000)
    }
  }

  async function submitUpload(file: File) {
    uploadLoading.value = true
    try {
      uploadResult.value = await uploadZip(file)
      taskId.value = uploadResult.value.taskId
      return uploadResult.value
    } finally {
      uploadLoading.value = false
    }
  }

  async function refreshFinalOutputs() {
    if (!taskId.value) throw new Error('请先选择 task')
    matchResult.value = await queryMatchTask(taskId.value)
    analysisRows.value = await listAnalysisByTask(taskId.value)
  }

  async function runExtractStep() {
    if (!taskId.value) throw new Error('请先选择 task')
    runningExtract.value = true
    try {
      const extractResp = await submitExtractTask(taskId.value, Math.floor(extractBatchSize.value))
      extractTaskId.value = extractResp.analyzeTaskId
      await pollTaskStatus(extractTaskId, extractStatus, queryExtractTask)
    } finally {
      runningExtract.value = false
    }
  }

  async function runHardFilterStep() {
    if (!taskId.value) throw new Error('请先选择 task')
    if (!jdText.value.trim()) throw new Error('请输入岗位 JD 文本')
    runningHardFilter.value = true
    try {
      const hardFilterResp = await submitHardFilterTask(taskId.value, { jdText: jdText.value.trim() })
      hardFilterTaskId.value = hardFilterResp.analyzeTaskId
      await pollTaskStatus(hardFilterTaskId, hardFilterStatus, queryHardFilterTask)
    } finally {
      runningHardFilter.value = false
    }
  }

  async function runHybridStep() {
    ensureRunParams()
    runningHybrid.value = true
    try {
      await submitMatchTask({
        taskId: taskId.value,
        jdText: jdText.value.trim(),
        topK: Math.floor(topK.value),
        recallK: Math.floor(recallK.value),
      })
      await pollMatchDone()
    } finally {
      runningHybrid.value = false
    }
  }

  async function runAnalyzeStep() {
    if (!taskId.value) throw new Error('请先选择 task')
    runningAnalyze.value = true
    try {
      const analyzeResp = await submitAnalyzeTaskWithBatch(taskId.value, Math.floor(analyzeBatchSize.value))
      analyzeTaskId.value = analyzeResp.analyzeTaskId
      await pollTaskStatus(analyzeTaskId, analyzeStatus, queryAnalyzeTask)
      analysisRows.value = await listAnalysisByTask(taskId.value)
    } finally {
      runningAnalyze.value = false
    }
  }

  async function runFullPipeline() {
    ensureRunParams()
    runningPipeline.value = true
    try {
      await runExtractStep()
      await runHardFilterStep()
      await runHybridStep()
      await runAnalyzeStep()
      await refreshFinalOutputs()
    } finally {
      runningPipeline.value = false
    }
  }

  return {
    uploadLoading,
    uploadResult,
    taskId,
    jdText,
    topK,
    recallK,
    extractBatchSize,
    analyzeBatchSize,
    runningPipeline,
    runningExtract,
    runningHardFilter,
    runningHybrid,
    runningAnalyze,
    extractTaskId,
    hardFilterTaskId,
    analyzeTaskId,
    extractStatus,
    hardFilterStatus,
    analyzeStatus,
    matchResult,
    analysisRows,
    matchItems,
    hasResult,
    parsedAnalysisRows,
    submitUpload,
    runExtractStep,
    runHardFilterStep,
    runHybridStep,
    runAnalyzeStep,
    runFullPipeline,
    refreshFinalOutputs,
  }
}

