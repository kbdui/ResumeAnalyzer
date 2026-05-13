import { del, get, post } from './http'
import type {
  AnalysisItem,
  AnalyzeSubmitResponse,
  AnalyzeTaskStatus,
  ExtractedResumeItem,
  HybridResultItem,
  PythonTaskResultPayload,
  TaskItem,
  TaskResumeItem,
  UploadResponse,
} from './types'

export function uploadZip(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return post<UploadResponse>('/save/zip/texts', formData)
}

export function listTasks() {
  return get<TaskItem[]>('/task/list')
}

export function deleteTask(taskId: string) {
  return del<boolean>(`/task/${taskId}`)
}

export function submitMatchTask(payload: {
  taskId: string
  jdText: string
  topK?: number
  recallK?: number
}) {
  return post<string>('/hybrid/match/task', payload)
}

export function queryMatchTask(taskId: string) {
  return get<PythonTaskResultPayload>(`/hybrid/match/task/${taskId}`)
}

export function listHybridResultsByTask(taskId: string) {
  return get<HybridResultItem[]>(`/hybrid/${taskId}/results`)
}

export function submitExtractTask(taskId: string, batchSize = 5) {
  return post<AnalyzeSubmitResponse>(`/deepseek/${taskId}/extract?batchSize=${batchSize}`)
}

export function queryExtractTask(extractTaskId: string) {
  return get<AnalyzeTaskStatus>(`/deepseek/extract/${extractTaskId}`)
}

export function submitHardFilterTask(taskId: string, payload: { jdText: string }, batchSize = 5) {
  return post<AnalyzeSubmitResponse>(`/deepseek/${taskId}/hard-filter?batchSize=${batchSize}`, payload)
}

export function queryHardFilterTask(hardFilterTaskId: string) {
  return get<AnalyzeTaskStatus>(`/deepseek/hard-filter/${hardFilterTaskId}`)
}

export function submitAnalyzeTask(taskId: string, analyzeCount?: number) {
  const suffix = Number.isFinite(analyzeCount) ? `?analyzeCount=${Math.floor(analyzeCount as number)}` : ''
  return post<AnalyzeSubmitResponse>(`/deepseek/${taskId}/analyze${suffix}`)
}

export function submitAnalyzeTaskWithBatch(taskId: string, batchSize = 5, analyzeCount?: number) {
  const params = new URLSearchParams({ batchSize: String(batchSize) })
  if (Number.isFinite(analyzeCount)) {
    params.set('analyzeCount', String(Math.floor(analyzeCount as number)))
  }
  return post<AnalyzeSubmitResponse>(`/deepseek/${taskId}/analyze?${params.toString()}`)
}

export function queryAnalyzeTask(analyzeTaskId: string) {
  return get<AnalyzeTaskStatus>(`/deepseek/analyze/${analyzeTaskId}`)
}

export function listAnalysisByTask(taskId: string) {
  return get<AnalysisItem[]>(`/deepseek/${taskId}/analysis`)
}

export function listExtractedResumeByTask(taskId: string) {
  return get<ExtractedResumeItem[]>(`/resume/task/${taskId}`)
}

export function listTaskResumeByTask(taskId: string) {
  return get<TaskResumeItem[]>(`/task-resume/${taskId}/list`)
}
