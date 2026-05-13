import { computed, ref, watch, type Ref } from 'vue'
import {
  listAnalysisByTask,
  listHybridResultsByTask,
  listTaskResumeByTask,
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
import type {
  AnalyzeTaskStatus,
  AnalysisItem,
  HybridResultItem,
  PythonTaskItem,
  PythonTaskResultPayload,
  UploadResponse,
} from '@/api/types'

export function useWorkflowRunner() {
  let taskSelectionRefreshSeq = 0

  const uploadLoading = ref(false)
  const uploadResult = ref<UploadResponse | null>(null)

  const taskId = ref('')
  const jdText = ref('')
  const topK = ref(20)
  const recallK = ref(200)
  const extractBatchSize = ref(8)
  const hardFilterBatchSize = ref(8)
  const analyzeBatchSize = ref(8)
  const analyzeCount = ref(20)
  const runningPipeline = ref(false)
  const runningExtract = ref(false)
  const runningHardFilter = ref(false)
  const runningHybrid = ref(false)
  const runningAnalyze = ref(false)
  const runningAnyStep = computed(
    () =>
      runningPipeline.value ||
      runningExtract.value ||
      runningHardFilter.value ||
      runningHybrid.value ||
      runningAnalyze.value,
  )

  const extractTaskId = ref('')
  const hardFilterTaskId = ref('')
  const analyzeTaskId = ref('')

  const extractStatus = ref<AnalyzeTaskStatus | null>(null)
  const hardFilterStatus = ref<AnalyzeTaskStatus | null>(null)
  const analyzeStatus = ref<AnalyzeTaskStatus | null>(null)
  const matchResult = ref<PythonTaskResultPayload | null>(null)
  const analysisRows = ref<AnalysisItem[]>([])
  const taskResumePassCount = ref(0)

  const matchItems = computed<PythonTaskItem[]>(() => matchResult.value?.result?.results?.items || [])
  const recallSelectedCount = computed(() => matchResult.value?.result?.summary?.top_k || matchItems.value.length)
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

  function normalizePositiveInt(value: number, fallback = 1) {
    const n = Math.floor(Number(value))
    return Number.isFinite(n) && n >= 1 ? n : fallback
  }

  function clampTopKAndRecallK() {
    const normalizedRecall = normalizePositiveInt(recallK.value)
    recallK.value = taskResumePassCount.value > 0 ? Math.min(normalizedRecall, taskResumePassCount.value) : normalizedRecall
    topK.value = Math.min(normalizePositiveInt(topK.value), recallK.value)
  }

  function clampAnalyzeCount() {
    const normalizedAnalyzeCount = normalizePositiveInt(analyzeCount.value)
    analyzeCount.value = recallSelectedCount.value > 0 ? Math.min(normalizedAnalyzeCount, recallSelectedCount.value) : normalizedAnalyzeCount
  }

  function ensureRunParams() {
    if (!taskId.value) throw new Error('请先选择 task')
    if (!jdText.value.trim()) throw new Error('请输入岗位 JD 文本')
    if (!Number.isFinite(topK.value) || topK.value < 1) throw new Error('topK 必须为正整数')
    if (!Number.isFinite(recallK.value) || recallK.value < 1) throw new Error('recallK 必须为正整数')
    if (taskResumePassCount.value < 1) throw new Error('当前没有通过硬过滤的简历')
    if (recallK.value > taskResumePassCount.value) throw new Error('recallK 不能大于通过硬过滤的简历数')
    if (topK.value > recallK.value) throw new Error('topK 不能大于 recallK')
  }

  function sleep(ms: number) {
    return new Promise((resolve) => window.setTimeout(resolve, ms))
  }

  function parseStoredMatchResult(rows: HybridResultItem[]) {
    const latest = rows.find((row) => row.resultJson && row.resultJson.trim())
    if (!latest?.resultJson) {
      return null
    }
    return JSON.parse(latest.resultJson) as PythonTaskResultPayload
  }

  async function refreshStoredMatchResult() {
    if (!taskId.value) {
      matchResult.value = null
      return
    }
    const rows = await listHybridResultsByTask(taskId.value)
    matchResult.value = parseStoredMatchResult(rows)
  }

  function ensureNoConcurrentStep() {
    if (runningAnyStep.value) {
      throw new Error('已有步骤正在执行，请等待完成后再操作')
    }
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
    await refreshStoredMatchResult()
    analysisRows.value = await listAnalysisByTask(taskId.value)
    const taskResumes = await listTaskResumeByTask(taskId.value)
    taskResumePassCount.value = taskResumes.filter((row) => row.pass).length
    clampTopKAndRecallK()
    clampAnalyzeCount()
  }

  async function refreshTaskResumePassCount() {
    if (!taskId.value) return
    const taskResumes = await listTaskResumeByTask(taskId.value)
    taskResumePassCount.value = taskResumes.filter((row) => row.pass).length
    clampTopKAndRecallK()
  }

  async function refreshCountsForTaskSelection(selectedTaskId: string) {
    const currentSeq = ++taskSelectionRefreshSeq

    try {
      const taskResumes = await listTaskResumeByTask(selectedTaskId)
      if (currentSeq !== taskSelectionRefreshSeq || taskId.value !== selectedTaskId) {
        return
      }
      taskResumePassCount.value = taskResumes.filter((row) => row.pass).length
      clampTopKAndRecallK()
    } catch {
      if (currentSeq !== taskSelectionRefreshSeq || taskId.value !== selectedTaskId) {
        return
      }
      taskResumePassCount.value = 0
      clampTopKAndRecallK()
    }

    try {
      const rows = await listHybridResultsByTask(selectedTaskId)
      if (currentSeq !== taskSelectionRefreshSeq || taskId.value !== selectedTaskId) {
        return
      }
      matchResult.value = parseStoredMatchResult(rows)
      clampAnalyzeCount()
    } catch {
      if (currentSeq !== taskSelectionRefreshSeq || taskId.value !== selectedTaskId) {
        return
      }
      matchResult.value = null
      clampAnalyzeCount()
    }

    try {
      const rows = await listAnalysisByTask(selectedTaskId)
      if (currentSeq !== taskSelectionRefreshSeq || taskId.value !== selectedTaskId) {
        return
      }
      analysisRows.value = rows
    } catch {
      if (currentSeq !== taskSelectionRefreshSeq || taskId.value !== selectedTaskId) {
        return
      }
      analysisRows.value = []
    }
  }

  async function runExtractStepCore() {
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

  async function runHardFilterStepCore() {
    if (!taskId.value) throw new Error('请先选择 task')
    if (!jdText.value.trim()) throw new Error('请输入岗位 JD 文本')
    runningHardFilter.value = true
    try {
      const hardFilterResp = await submitHardFilterTask(
        taskId.value,
        { jdText: jdText.value.trim() },
        Math.floor(hardFilterBatchSize.value),
      )
      hardFilterTaskId.value = hardFilterResp.analyzeTaskId
      await pollTaskStatus(hardFilterTaskId, hardFilterStatus, queryHardFilterTask)
      await refreshTaskResumePassCount()
    } finally {
      runningHardFilter.value = false
    }
  }

  async function runHybridStepCore() {
    await refreshTaskResumePassCount()
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
      await refreshStoredMatchResult()
      clampAnalyzeCount()
    } finally {
      runningHybrid.value = false
    }
  }

  async function runAnalyzeStepCore() {
    if (!taskId.value) throw new Error('请先选择 task')
    if (!matchResult.value || matchResult.value.status !== 'done') {
      await refreshStoredMatchResult()
    }
    if (!matchResult.value || matchResult.value.status !== 'done') {
      matchResult.value = await queryMatchTask(taskId.value)
    }
    if (recallSelectedCount.value < 1) throw new Error('当前没有通过召回筛选的简历')
    clampAnalyzeCount()
    if (analyzeCount.value > recallSelectedCount.value) {
      throw new Error('评估简历数量不能大于通过召回筛选的简历数量')
    }
    runningAnalyze.value = true
    try {
      const analyzeResp = await submitAnalyzeTaskWithBatch(
        taskId.value,
        Math.floor(analyzeBatchSize.value),
        Math.floor(analyzeCount.value),
      )
      analyzeTaskId.value = analyzeResp.analyzeTaskId
      await pollTaskStatus(analyzeTaskId, analyzeStatus, queryAnalyzeTask)
      analysisRows.value = await listAnalysisByTask(taskId.value)
    } finally {
      runningAnalyze.value = false
    }
  }

  async function runExtractStep() {
    ensureNoConcurrentStep()
    await runExtractStepCore()
  }

  async function runHardFilterStep() {
    ensureNoConcurrentStep()
    await runHardFilterStepCore()
  }

  async function runHybridStep() {
    ensureNoConcurrentStep()
    await runHybridStepCore()
  }

  async function runAnalyzeStep() {
    ensureNoConcurrentStep()
    await runAnalyzeStepCore()
  }

  async function runFullPipeline() {
    ensureNoConcurrentStep()
    if (!taskId.value) throw new Error('请先选择 task')
    if (!jdText.value.trim()) throw new Error('请输入岗位 JD 文本')
    runningPipeline.value = true
    try {
      await runExtractStepCore()
      await runHardFilterStepCore()
      await runHybridStepCore()
      await runAnalyzeStepCore()
      await refreshFinalOutputs()
    } finally {
      runningPipeline.value = false
    }
  }

  watch(taskId, (newTaskId) => {
    taskSelectionRefreshSeq += 1
    matchResult.value = null
    analysisRows.value = []
    taskResumePassCount.value = 0
    clampTopKAndRecallK()
    clampAnalyzeCount()

    if (newTaskId) {
      void refreshCountsForTaskSelection(newTaskId)
    }
  })

  watch(taskResumePassCount, () => {
    clampTopKAndRecallK()
  })

  watch(recallSelectedCount, () => {
    clampAnalyzeCount()
  })

  return {
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
  }
}
