import axios from 'axios'
import type {
  ApiResponse,
  ChatAnswerResponse,
  ChatMessageResponse,
  ChatSessionResponse,
  CreateChatSessionRequest,
  CreateKnowledgeBaseRequest,
  DocumentResponse,
  DocumentStatusResponse,
  KnowledgeBaseResponse,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  SendChatMessageRequest,
  UpdateKnowledgeBaseRequest,
  UserResponse,
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
}
