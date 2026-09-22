export type ApiResponse<T> = {
  code: number
  message: string
  data?: T   //泛型且可以不存在
}

export type UserRole = 'USER' | 'ADMIN'

export type UserStatus = 'ENABLED' | 'DISABLED'

export type UserResponse = {
  id: number
  username: string
  nickname: string | null
  role: UserRole
  status: UserStatus
  createdAt: string
}

export type LoginResponse = {
  token: string
  tokenType: 'Bearer'
  expiresIn: number
  user: UserResponse
}

export type RegisterRequest = {
  username: string
  password: string
  nickname?: string
}

export type LoginRequest = {
  username: string
  password: string
}

export type KnowledgeBaseResponse = {
  id: number
  userId: number
  name: string
  description: string | null
  documentCount: number
  createdAt: string
  updatedAt: string
}

export type CreateKnowledgeBaseRequest = {
  name: string
  description?: string
}

export type UpdateKnowledgeBaseRequest = {
  name: string
  description?: string
}

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export type DocumentResponse = {
  id: number
  knowledgeBaseId: number
  userId: number
  fileName: string
  fileType: string
  fileSize: number
  status: DocumentStatus
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export type DocumentStatusResponse = {
  id: number
  status: DocumentStatus
  errorMessage: string | null
  updatedAt: string
}

export type ChatRole = 'USER' | 'ASSISTANT'

export type PromptMode = 'STRICT_KB' | 'KB_FIRST' | 'GENERAL' | 'LEARNING'

export type CreateChatSessionRequest = {
  knowledgeBaseId: number
  title?: string
}

export type ChatSessionResponse = {
  id: number
  knowledgeBaseId: number
  title: string
  createdAt: string
  updatedAt: string
}

export type SendChatMessageRequest = {
  question: string
  topK?: number
  promptMode?: PromptMode
}

export type ChatReferenceResponse = {
  documentId: number
  chunkId: number
  chunkIndex: number
  content: string
  similarity: number
  fileName: string | null
  pageNumber: number | null
  pdfPageNumber: number | null
}

export type ChatMessageResponse = {
  id: number
  sessionId: number
  role: ChatRole
  content: string
  createdAt: string
  references: ChatReferenceResponse[]
}

export type ChatAnswerResponse = {
  sessionId: number
  userMessage: ChatMessageResponse
  assistantMessage: ChatMessageResponse
  references: ChatReferenceResponse[]
}
