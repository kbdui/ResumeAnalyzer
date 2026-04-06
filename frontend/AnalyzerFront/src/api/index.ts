import { get, post } from './http'
import type {
  AnalysisItem,
  AnalyzeSubmitResponse,
  AnalyzeTaskStatus,
  PythonTaskResultPayload,
  TaskItem,
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

export function submitExtractTask(taskId: string, batchSize = 5) {
  return post<AnalyzeSubmitResponse>(`/deepseek/${taskId}/extract?batchSize=${batchSize}`)
}

export function submitHardFilterTask(taskId: string, payload: { jdText: string }) {
  return post<AnalyzeSubmitResponse>(`/deepseek/${taskId}/hard-filter`, payload)
}

export function queryHardFilterTask(hardFilterTaskId: string) {
  return get<AnalyzeTaskStatus>(`/deepseek/hard-filter/${hardFilterTaskId}`)
}

export function submitAnalyzeTask(taskId: string) {
  return post<AnalyzeSubmitResponse>(`/deepseek/${taskId}/analyze`)
}

export function submitAnalyzeTaskWithBatch(taskId: string, batchSize = 5) {
  return post<AnalyzeSubmitResponse>(`/deepseek/${taskId}/analyze?batchSize=${batchSize}`)
}

export function queryAnalyzeTask(analyzeTaskId: string) {
  return get<AnalyzeTaskStatus>(`/deepseek/analyze/${analyzeTaskId}`)
}

export function listAnalysisByTask(taskId: string) {
  return get<AnalysisItem[]>(`/deepseek/${taskId}/analysis`)
}
