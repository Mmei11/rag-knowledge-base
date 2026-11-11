import axios from 'axios'
import type {
  AddKnowledgeBaseMemberRequest,
  ApiResponse,
  ChatAnswerResponse,
  ChatMessageResponse,
  ChatSessionResponse,
  ChatStreamDeltaResponse,
  ChatStreamErrorResponse,
  CreateChatSessionRequest,
  CreateKnowledgeBaseRequest,
  DocumentResponse,
  DocumentStatusResponse,
  KnowledgeBaseMemberResponse,
  KnowledgeBaseResponse,
  LoginRequest,
  LoginResponse,
  OperationAction,
  OperationLogResponse,
  OperationResourceType,
  PageResponse,
  RegisterRequest,
  SendChatMessageRequest,
  UpdateKnowledgeBaseMemberRoleRequest,
  UpdateKnowledgeBaseRequest,
  UpdateChatSessionRequest,
  UserResponse,
  UserRole,
  UserStatus,
} from '../types/api'

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ??
  'http://localhost:8080'

const client = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    Accept: 'application/json',
  },
})

let accessTokenGetter: (() => string | null) | null = null
let unauthorizedHandler: (() => void) | null = null

export class ApiError extends Error {
  status: number
  code?: number
  data?: unknown

  constructor(message: string, status: number, code?: number, data?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.data = data
  }
}

export function configureHttpAuth(options: {
  getAccessToken: () => string | null
  onUnauthorized: () => void
}) {
  accessTokenGetter = options.getAccessToken
  unauthorizedHandler = options.onUnauthorized
}

type RequestOptions = {
  method?: string
  body?: unknown
  auth?: boolean
  onUploadProgress?: (percent: number) => void
}

type ChatStreamHandlers = {
  onUserMessage: (message: ChatMessageResponse) => void
  onDelta: (content: string) => void
  onComplete: (answer: ChatAnswerResponse) => void
}

type SseFrame = {
  eventName: string
  data: string
}

async function apiRequest<T>(
  path: string,
  { method = 'GET', body, auth = true, onUploadProgress }: RequestOptions = {},
): Promise<T> {
  const token = auth ? accessTokenGetter?.() : null

  try {
    const response = await client.request<ApiResponse<T>>({
      url: path,
      method,
      data: body,
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      onUploadProgress: onUploadProgress
        ? (event) => {
            if (!event.total) {
              return
            }
            onUploadProgress(Math.round((event.loaded * 100) / event.total))
          }
        : undefined,
    })

    const payload = response.data
    if (!payload || payload.code !== 0) {
      if (response.status === 401 || payload?.code === 40100) {
        unauthorizedHandler?.()
      }
      throw new ApiError(
        payload?.message ?? '请求失败',
        response.status,
        payload?.code,
        payload?.data,
      )
    }

    return payload.data as T
  } catch (err) {
    if (err instanceof ApiError) {
      throw err
    }

    if (axios.isAxiosError<ApiResponse<unknown>>(err)) {
      const status = err.response?.status ?? 0
      const payload = err.response?.data

      if (status === 401 || payload?.code === 40100) {
        unauthorizedHandler?.()
      }

      throw new ApiError(
        payload?.message ?? err.message,
        status,
        payload?.code,
        payload?.data,
      )
    }

    throw new ApiError('操作失败', 0)
  }
}

function parseSseFrame(frame: string): SseFrame | null {
  const data: string[] = []
  let eventName = 'message'

  for (const line of frame.split(/\r?\n/)) {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim()
    }
    if (line.startsWith('data:')) {
      data.push(line.slice('data:'.length).replace(/^ /, ''))
    }
  }

  return data.length > 0 ? { eventName, data: data.join('\n') } : null
}

async function streamRequestError(response: Response) {
  let payload: ApiResponse<unknown> | undefined
  try {
    payload = (await response.json()) as ApiResponse<unknown>
  } catch {
    // 非 JSON 的网关错误使用状态文本作为降级提示。
  }

  if (response.status === 401 || payload?.code === 40100) {
    unauthorizedHandler?.()
  }
  return new ApiError(
    payload?.message || response.statusText || '请求失败',
    response.status,
    payload?.code,
    payload?.data,
  )
}

function handleStreamFrame(frame: SseFrame, handlers: ChatStreamHandlers) {
  if (frame.eventName === 'user') {
    handlers.onUserMessage(JSON.parse(frame.data) as ChatMessageResponse)
    return null
  }
  if (frame.eventName === 'delta') {
    const delta = JSON.parse(frame.data) as ChatStreamDeltaResponse
    handlers.onDelta(delta.content)
    return null
  }
  if (frame.eventName === 'complete') {
    handlers.onComplete(JSON.parse(frame.data) as ChatAnswerResponse)
    return null
  }
  if (frame.eventName === 'error') {
    const error = JSON.parse(frame.data) as ChatStreamErrorResponse
    return new ApiError(error.message || '生成回答失败，请稍后重试', 0)
  }
  return null
}

async function streamChatMessage(
  sessionId: number,
  request: SendChatMessageRequest,
  handlers: ChatStreamHandlers,
  signal: AbortSignal,
) {
  const token = accessTokenGetter?.()
  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}/api/chat/sessions/${sessionId}/messages/stream`, {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(request),
      signal,
    })
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      throw err
    }
    throw new ApiError('网络连接失败，请检查后端服务是否启动', 0)
  }

  if (!response.ok) {
    throw await streamRequestError(response)
  }
  if (!response.body) {
    throw new ApiError('浏览器不支持流式回答', 0)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let completed = false
  let streamError: ApiError | null = null

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const frames = buffer.split(/\r?\n\r?\n/)
      buffer = frames.pop() ?? ''
      for (const rawFrame of frames) {
        const frame = parseSseFrame(rawFrame)
        if (!frame) {
          continue
        }
        const error = handleStreamFrame(frame, handlers)
        if (error) {
          streamError = error
          break
        }
        if (frame.eventName === 'complete') {
          completed = true
        }
      }
      if (streamError || completed) {
        break
      }
    }

    buffer += decoder.decode()
    if (!streamError && !completed && buffer) {
      const frame = parseSseFrame(buffer)
      if (frame) {
        streamError = handleStreamFrame(frame, handlers)
        completed = frame.eventName === 'complete'
      }
    }
  } finally {
    if (streamError || completed) {
      await reader.cancel()
    }
    reader.releaseLock()
  }

  if (streamError) {
    throw streamError
  }
  if (!completed) {
    throw new ApiError('回答流意外中断，请重试', 0)
  }
}

function buildQuery(
  params: Record<string, string | number | undefined | null>,
) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue
    }
    search.set(key, String(value))
  }
  const query = search.toString()
  return query ? `?${query}` : ''
}

export const authApi = {
  register(request: RegisterRequest) {
    return apiRequest<UserResponse>('/api/auth/register', {
      method: 'POST',
      body: request,
      auth: false,
    })
  },

  login(request: LoginRequest) {
    return apiRequest<LoginResponse>('/api/auth/login', {
      method: 'POST',
      body: request,
      auth: false,
    })
  },
}

export const knowledgeBaseApi = {
  list() {
    return apiRequest<KnowledgeBaseResponse[]>('/api/knowledge-bases')
  },

  create(request: CreateKnowledgeBaseRequest) {
    return apiRequest<KnowledgeBaseResponse>('/api/knowledge-bases', {
      method: 'POST',
      body: request,
    })
  },

  update(id: number, request: UpdateKnowledgeBaseRequest) {
    return apiRequest<KnowledgeBaseResponse>(`/api/knowledge-bases/${id}`, {
      method: 'PUT',
      body: request,
    })
  },

  remove(id: number) {
    return apiRequest<void>(`/api/knowledge-bases/${id}`, {
      method: 'DELETE',
    })
  },
}

export const knowledgeBaseMemberApi = {
  list(knowledgeBaseId: number) {
    return apiRequest<KnowledgeBaseMemberResponse[]>(
      `/api/knowledge-bases/${knowledgeBaseId}/members`,
    )
  },

  add(knowledgeBaseId: number, request: AddKnowledgeBaseMemberRequest) {
    return apiRequest<KnowledgeBaseMemberResponse>(
      `/api/knowledge-bases/${knowledgeBaseId}/members`,
      {
        method: 'POST',
        body: request,
      },
    )
  },

  updateRole(
    knowledgeBaseId: number,
    userId: number,
    request: UpdateKnowledgeBaseMemberRoleRequest,
  ) {
    return apiRequest<KnowledgeBaseMemberResponse>(
      `/api/knowledge-bases/${knowledgeBaseId}/members/${userId}`,
      {
        method: 'PUT',
        body: request,
      },
    )
  },

  remove(knowledgeBaseId: number, userId: number) {
    return apiRequest<void>(
      `/api/knowledge-bases/${knowledgeBaseId}/members/${userId}`,
      {
        method: 'DELETE',
      },
    )
  },
}

export const documentApi = {
  listByKnowledgeBase(knowledgeBaseId: number) {
    return apiRequest<DocumentResponse[]>(
      `/api/knowledge-bases/${knowledgeBaseId}/documents`,
    )
  },

  upload(
    knowledgeBaseId: number,
    file: File,
    onUploadProgress?: (percent: number) => void,
  ) {
    const formData = new FormData()
    formData.append('file', file)

    return apiRequest<DocumentResponse>(
      `/api/knowledge-bases/${knowledgeBaseId}/documents`,
      {
        method: 'POST',
        body: formData,
        onUploadProgress,
      },
    )
  },

  getStatus(documentId: number) {
    return apiRequest<DocumentStatusResponse>(`/api/documents/${documentId}/status`)
  },

  reprocess(documentId: number) {
    return apiRequest<DocumentStatusResponse>(
      `/api/documents/${documentId}/reprocess`,
      {
        method: 'POST',
      },
    )
  },

  remove(documentId: number) {
    return apiRequest<void>(`/api/documents/${documentId}`, {
      method: 'DELETE',
    })
  },
}

export const chatApi = {
  createSession(request: CreateChatSessionRequest) {
    return apiRequest<ChatSessionResponse>('/api/chat/sessions', {
      method: 'POST',
      body: request,
    })
  },

  listSessions() {
    return apiRequest<ChatSessionResponse[]>('/api/chat/sessions')
  },

  renameSession(sessionId: number, request: UpdateChatSessionRequest) {
    return apiRequest<ChatSessionResponse>(`/api/chat/sessions/${sessionId}`, {
      method: 'PUT', body: request,
    })
  },

  deleteSession(sessionId: number) {
    return apiRequest<void>(`/api/chat/sessions/${sessionId}`, { method: 'DELETE' })
  },

  clearMessages(sessionId: number) {
    return apiRequest<void>(`/api/chat/sessions/${sessionId}/messages`, { method: 'DELETE' })
  },

  suggestQuestions(sessionId: number) {
    return apiRequest<string[]>(`/api/chat/sessions/${sessionId}/suggestions`)
  },

  listMessages(sessionId: number) {
    return apiRequest<ChatMessageResponse[]>(
      `/api/chat/sessions/${sessionId}/messages`,
    )
  },

  sendMessage(sessionId: number, request: SendChatMessageRequest) {
    return apiRequest<ChatAnswerResponse>(
      `/api/chat/sessions/${sessionId}/messages`,
      {
        method: 'POST',
        body: request,
      },
    )
  },

  streamMessage(
    sessionId: number,
    request: SendChatMessageRequest,
    handlers: ChatStreamHandlers,
    signal: AbortSignal,
  ) {
    return streamChatMessage(sessionId, request, handlers, signal)
  },
}

export const adminApi = {
  listUsers(params: {
    keyword?: string
    status?: UserStatus
    role?: UserRole
    page?: number
    size?: number
  }) {
    return apiRequest<PageResponse<UserResponse>>(
      `/api/admin/users${buildQuery(params)}`,
    )
  },

  updateUserStatus(id: number, status: UserStatus) {
    return apiRequest<UserResponse>(`/api/admin/users/${id}/status`, {
      method: 'PATCH',
      body: { status },
    })
  },

  updateUserRole(id: number, role: UserRole) {
    return apiRequest<UserResponse>(`/api/admin/users/${id}/role`, {
      method: 'PATCH',
      body: { role },
    })
  },

  listOperationLogs(params: {
    userId?: number
    action?: OperationAction
    resourceType?: OperationResourceType
    resourceId?: number
    from?: string
    to?: string
    page?: number
    size?: number
  }) {
    return apiRequest<PageResponse<OperationLogResponse>>(
      `/api/admin/operation-logs${buildQuery(params)}`,
    )
  },
}
