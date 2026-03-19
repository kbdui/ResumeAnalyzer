const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

type RequestMethod = 'GET' | 'POST'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

async function request<T>(method: RequestMethod, path: string, body?: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: body instanceof FormData ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
    })
  } catch {
    throw new Error(`无法连接后端服务：${BASE_URL}（请确认 Java 后端已启动）`)
  }

  let payload: ApiResponse<T>
  try {
    payload = (await response.json()) as ApiResponse<T>
  } catch {
    throw new Error(`后端响应解析失败：${response.status}`)
  }

  if (!response.ok || payload.code !== 200) {
    throw new Error(payload.message || `请求失败：${response.status}`)
  }
  return payload.data
}

export function get<T>(path: string): Promise<T> {
  return request<T>('GET', path)
}

export function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>('POST', path, body)
}

export { BASE_URL }
