export interface TaskItem {
  id: number
  taskId: string
  pythonTaskId?: string
  resumeCount: number
  createTime: string
  submitted: number
  updateTime: string
}

export interface UploadResponse {
  taskId: string
  resumeCount: number
  savedCount: number
}

export interface PythonTaskItem {
  resume_id: string
  file_name: string
  text: string
  recall_score: number
  tfidf_score: number
  keyword_coverage: number
  top_terms: string[]
  embedding_score: number
  final_score: number
}

export interface PythonTaskResultPayload {
  task_id: string
  status: 'queued' | 'running' | 'done' | 'failed' | 'not_found'
  created_at?: number
  started_at?: number
  ended_at?: number
  error?: string | null
  result?: {
    summary: {
      total_resumes: number
      recall_count: number
      top_k: number
      elapsed_ms: number
    }
    results: {
      model: string
      embedding_fallback: boolean
      embedding_error?: string | null
      items: PythonTaskItem[]
    }
  } | null
}

export interface AnalysisItem {
  id: number
  taskId: number
  resumeId: string
  analysisJson: string
  createTime: string
  updateTime: string
}

export interface AnalyzeSubmitResponse {
  analyzeTaskId: string
  taskId: string
  message: string
}

export interface AnalyzeTaskStatus {
  analyzeTaskId: string
  taskId: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED'
  total: number
  successCount: number
  failedCount: number
  error?: string | null
  startedAtMs?: number
  endedAtMs?: number
}

export interface ResumeDetail {
  personalInfo?: {
    name?: string
    contact?: string
    email?: string
  }
  education?: Array<{
    school?: string
    major?: string
    degree?: string
    graduationYear?: string
  }>
  workExperience?: Array<{
    company?: string
    position?: string
    duration?: string
    description?: string
  }>
  skills?: string[]
  projects?: Array<{
    name?: string
    description?: string
    technologies?: string[]
  }>
  certificates?: string[]
}

export interface TaskResumeMainView {
  relationId: number
  resumeId: number
  rankNo?: number
  finalScore?: number
  createTime: string
  resume: ResumeDetail
}
