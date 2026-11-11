export type ApiResponse<T> = {
  code: number
  message: string
  data?: T   //泛型且可以不存在
}

export type PageResponse<T> = {
  items: T[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

export type UserRole = 'USER' | 'ADMIN' | 'SUPER_ADMIN'

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

export type KnowledgeBaseRole = 'OWNER' | 'EDITOR' | 'VIEWER'

export type KnowledgeBaseMemberRole = 'EDITOR' | 'VIEWER'

export type KnowledgeBaseResponse = {
  id: number
  userId: number
  name: string
  description: string | null
  documentCount: number
  createdAt: string
  updatedAt: string
  role: KnowledgeBaseRole
}

export type KnowledgeBaseMemberResponse = {
  userId: number
  username: string
  nickname: string | null
  role: KnowledgeBaseMemberRole
  createdAt: string
}

export type AddKnowledgeBaseMemberRequest = {
  username: string
  role: KnowledgeBaseMemberRole
}

export type UpdateKnowledgeBaseMemberRoleRequest = {
  role: KnowledgeBaseMemberRole
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
  parseProgress: number
  parseStage: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export type DocumentStatusResponse = {
  id: number
  status: DocumentStatus
  parseProgress: number
  parseStage: string | null
  errorMessage: string | null
  updatedAt: string
}

export type ChatRole = 'USER' | 'ASSISTANT'

export type PromptMode = 'STRICT_KB' | 'KB_FIRST' | 'GENERAL' | 'LEARNING'

export type CreateChatSessionRequest = {
  knowledgeBaseId: number
  title?: string
}

export type UpdateChatSessionRequest = {
  title: string
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
  similarityThreshold?: number
  documentIds?: number[]
  startDate?: string
  endDate?: string
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

export type ChatStreamDeltaResponse = {
  content: string
}

export type ChatStreamErrorResponse = {
  message: string
}

export type UpdateUserStatusRequest = {
  status: UserStatus
}

export type UpdateUserRoleRequest = {
  role: UserRole
}

export type OperationAction =
  | 'USER_REGISTER'
  | 'USER_LOGIN'
  | 'USER_ENABLE'
  | 'USER_DISABLE'
  | 'USER_ROLE_CHANGE'
  | 'KB_CREATE'
  | 'KB_UPDATE'
  | 'KB_DELETE'
  | 'KB_MEMBER_ADD'
  | 'KB_MEMBER_ROLE_CHANGE'
  | 'KB_MEMBER_REMOVE'
  | 'DOCUMENT_UPLOAD'
  | 'DOCUMENT_DELETE'
  | 'DOCUMENT_REPROCESS'

export type OperationResourceType = 'USER' | 'KNOWLEDGE_BASE' | 'DOCUMENT'

export type OperationLogResponse = {
  id: number
  userId: number | null
  username: string | null
  action: OperationAction
  resourceType: OperationResourceType | null
  resourceId: number | null
  ip: string | null
  createdAt: string
}
