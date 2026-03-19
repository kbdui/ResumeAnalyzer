import { get, post } from './http'
import type {
  AnalyzeSubmitResponse,
  AnalyzeTaskStatus,
  PythonTaskResultPayload,
  TaskItem,
  TaskResumeMainView,
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
  return post<string>('/screen/match/task', payload)
}

export function queryMatchTask(taskId: string) {
  return get<PythonTaskResultPayload>(`/screen/match/task/${taskId}`)
}

export function submitAnalyzeTask(taskId: string) {
  return post<AnalyzeSubmitResponse>(`/deepseek/${taskId}/analyze`)
}

export function queryAnalyzeTask(analyzeTaskId: string) {
  return get<AnalyzeTaskStatus>(`/deepseek/analyze/${analyzeTaskId}`)
}

export function listResumeByTask(taskId: string) {
  return get<TaskResumeMainView[]>(`/resume/task/${taskId}`)
}
