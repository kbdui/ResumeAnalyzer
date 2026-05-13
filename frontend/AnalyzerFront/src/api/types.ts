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
  full_text_score?: number
  work_experience_score?: number
  project_score?: number
  skills_score?: number
  education_score?: number
  summary_score?: number
  experience_score?: number
  skills_recall_score?: number
  skills_coverage?: number
  role_alignment_score?: number
  role_alignment_reasons?: string[]
  negative_penalty?: number
  experience_reasons?: string[]
  penalty_reasons?: string[]
  segments?: Record<string, unknown>
  raw_final_score?: number
  display_score?: number
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

export interface HybridResultItem {
  id: number
  taskId: number
  status: string
  resultJson?: string | null
  createTime?: string
  updateTime?: string
}

export interface AnalysisItem {
  id: number
  taskId: number
  resumeId: string
  analysisJson: string
  createTime: string
  updateTime: string
}

export interface ExtractedResumeItem {
  resume_id?: string
  personal_info?: {
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
  work_experience?: Array<{
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

export interface TaskResumeItem {
  id: number
  taskId: number
  resumeId: string
  pass: boolean
  analysisJson?: string | null
  createTime?: string
  updateTime?: string
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
