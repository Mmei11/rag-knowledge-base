import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { ApiError, chatApi, documentApi, knowledgeBaseApi } from '../api/http'
import { StatusMessage } from '../components/StatusMessage'
import type {
  ChatMessageResponse,
  ChatReferenceResponse,
  ChatSessionResponse,
  DocumentResponse,
  KnowledgeBaseResponse,
  PromptMode,
} from '../types/api'
import { formatDateTime } from '../utils/date'

const DEFAULT_TOP_K = 5
const DEFAULT_SIMILARITY_THRESHOLD = 0.35
const REFERENCE_PREVIEW_LIMIT = 320
const PROMPT_MODE_OPTIONS: Array<{ label: string; value: PromptMode }> = [
  { label: '知识库优先', value: 'KB_FIRST' },
  { label: '严格知识库', value: 'STRICT_KB' },
  { label: '通用问答', value: 'GENERAL' },
  { label: '学习讲解', value: 'LEARNING' },
]

function validateQuestion(question: string) {
  const text = question.trim()
  if (!text) {
    return '问题不能为空'
  }
  if (text.length > 1000) {
    return '问题不能超过 1000 个字符'
  }
  return ''
}

function validateSessionTitle(title: string) {
  if (title.length > 200) {
    return '会话标题不能超过 200 个字符'
  }
  return ''
}

function referenceLabel(reference: ChatReferenceResponse) {
  const fileName = reference.fileName || `文档 ${reference.documentId}`
  const page = reference.pageNumber ? ` · 第 ${reference.pageNumber} 页` : ''
  return `${fileName}${page}`
}

function similarityLabel(similarity: number) {
  return `${(similarity * 100).toFixed(1)}%`
}

function questionForMessage(messages: ChatMessageResponse[], messageIndex: number) {
  for (let index = messageIndex - 1; index >= 0; index -= 1) {
    if (messages[index].role === 'USER') {
      return messages[index].content
    }
  }
  return ''
}

function highlightTerms(question: string) {
  const ignoredTerms = new Set(['什么', '怎么', '如何', '哪些', '这个', '那个', '一下', '关于', '是否'])
  const terms = new Set<string>()

  for (const phrase of question.match(/[\u4E00-\u9FFF]{2,}/g) ?? []) {
    terms.add(phrase)
    for (let index = 0; index < phrase.length - 1; index += 1) {
      terms.add(phrase.slice(index, index + 2))
    }
  }

  for (const word of question.match(/[a-zA-Z0-9_-]{2,}/g) ?? []) {
    terms.add(word)
  }

  return [...terms]
    .filter((term) => !ignoredTerms.has(term))
    .sort((left, right) => right.length - left.length)
    .slice(0, 12)
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function highlightedContent(content: string, question: string): ReactNode {
  const terms = highlightTerms(question)
  if (terms.length === 0) {
    return content
  }

  const expression = new RegExp(`(${terms.map(escapeRegExp).join('|')})`, 'gi')
  return content.split(expression).map((part, index) => {
    const matched = terms.some((term) => term.toLocaleLowerCase() === part.toLocaleLowerCase())
    return matched ? <mark key={`${part}-${index}`}>{part}</mark> : part
  })
}

export function ChatPage() {
  const messageListRef = useRef<HTMLDivElement | null>(null)
  const documentFilterRef = useRef<HTMLDivElement | null>(null)

  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseResponse[]>([])
  const [documents, setDocuments] = useState<DocumentResponse[]>([])
  const [sessions, setSessions] = useState<ChatSessionResponse[]>([])
  const [messages, setMessages] = useState<ChatMessageResponse[]>([])

  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState<number | null>(null)
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null)

  const [sessionTitle, setSessionTitle] = useState('')
  const [question, setQuestion] = useState('')
  const [promptMode, setPromptMode] = useState<PromptMode>('KB_FIRST')
  const [topK, setTopK] = useState(DEFAULT_TOP_K)
  const [similarityThreshold, setSimilarityThreshold] = useState(DEFAULT_SIMILARITY_THRESHOLD)
  const [selectedDocumentIds, setSelectedDocumentIds] = useState<number[]>([])
  const [documentFilterOpen, setDocumentFilterOpen] = useState(false)
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')

  const [activeReference, setActiveReference] = useState<ChatReferenceResponse | null>(null)
  const [activeReferenceQuestion, setActiveReferenceQuestion] = useState('')
  const [referenceExpanded, setReferenceExpanded] = useState(false)

  const [loading, setLoading] = useState(false)
  const [answering, setAnswering] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const selectedSession = useMemo(
    () => sessions.find((session) => session.id === selectedSessionId) ?? null,
    [sessions, selectedSessionId],
  )

  const selectedKnowledgeBase = useMemo(
    () =>
      knowledgeBases.find(
        (knowledgeBase) => knowledgeBase.id === selectedKnowledgeBaseId,
      ) ?? null,
    [knowledgeBases, selectedKnowledgeBaseId],
  )

  const documentFilterLabel = useMemo(() => {
    if (documents.length === 0) {
      return '暂无已解析文档'
    }
    if (selectedDocumentIds.length === 0) {
      return '全部已解析文档'
    }
    if (selectedDocumentIds.length === 1) {
      return documents.find((document) => document.id === selectedDocumentIds[0])?.fileName ?? '已选择 1 个文档'
    }
    return `已选择 ${selectedDocumentIds.length} 个文档`
  }, [documents, selectedDocumentIds])

  const runAction = useCallback(async (action: () => Promise<void>) => {
    setLoading(true)
    setError('')
    setMessage('')

    try {
      await action()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '操作失败')
    } finally {
      setLoading(false)
    }
  }, [])

  const loadSessions = useCallback(async () => {
    const items = await chatApi.listSessions()
    setSessions(items)
    setSelectedSessionId((current) => {
      if (current && items.some((item) => item.id === current)) {
        return current
      }
      return items[0]?.id ?? null
    })
  }, [])

  const loadKnowledgeBases = useCallback(async () => {
    const items = await knowledgeBaseApi.list()
    setKnowledgeBases(items)
    setSelectedKnowledgeBaseId((current) => {
      if (current && items.some((item) => item.id === current)) {
        return current
      }
      return items[0]?.id ?? null
    })
  }, [])

  const loadFilterDocuments = useCallback(async (knowledgeBaseId: number) => {
    const items = await documentApi.listByKnowledgeBase(knowledgeBaseId)
    const completedItems = items.filter((document) => document.status === 'COMPLETED')
    setDocuments(completedItems)
    setSelectedDocumentIds((current) =>
      current.filter((documentId) => completedItems.some((document) => document.id === documentId)),
    )
  }, [])

  const loadMessages = useCallback(async (sessionId: number) => {
    const items = await chatApi.listMessages(sessionId)
    setMessages(items)
    setActiveReference(null)
    setActiveReferenceQuestion('')
    setReferenceExpanded(false)
  }, [])

  useEffect(() => {
    void runAction(async () => {
      await Promise.all([loadKnowledgeBases(), loadSessions()])
    })
  }, [loadKnowledgeBases, loadSessions, runAction])

  useEffect(() => {
    if (!selectedKnowledgeBaseId) {
      setDocuments([])
      setSelectedDocumentIds([])
      return
    }

    void runAction(() => loadFilterDocuments(selectedKnowledgeBaseId))
  }, [loadFilterDocuments, runAction, selectedKnowledgeBaseId])

  useEffect(() => {
    if (!documentFilterOpen) {
      return
    }

    function closeDocumentFilter(event: PointerEvent) {
      if (!documentFilterRef.current?.contains(event.target as Node)) {
        setDocumentFilterOpen(false)
      }
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setDocumentFilterOpen(false)
      }
    }

    document.addEventListener('pointerdown', closeDocumentFilter)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeDocumentFilter)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [documentFilterOpen])

  useEffect(() => {
    if (!selectedSessionId) {
      setMessages([])
      setActiveReference(null)
      setActiveReferenceQuestion('')
      setReferenceExpanded(false)
      return
    }

    void runAction(() => loadMessages(selectedSessionId))
  }, [loadMessages, runAction, selectedSessionId])

  useEffect(() => {
    if (selectedSession) {
      setSelectedKnowledgeBaseId(selectedSession.knowledgeBaseId)
    }
  }, [selectedSession])

  useEffect(() => {
    const messageList = messageListRef.current
    if (!messageList) {
      return
    }

    messageList.scrollTop = messageList.scrollHeight
  }, [messages, answering, selectedSessionId])

  async function handleCreateSession(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!selectedKnowledgeBaseId) {
      setError('请先创建或选择知识库')
      setMessage('')
      return
    }

    const titleError = validateSessionTitle(sessionTitle)
    if (titleError) {
      setError(titleError)
      setMessage('')
      return
    }

    await runAction(async () => {
      const title = sessionTitle.trim()
      const created = await chatApi.createSession({
        knowledgeBaseId: selectedKnowledgeBaseId,
        title: title || undefined,
      })
      setSessionTitle('')
      await loadSessions()
      setSelectedSessionId(created.id)
      setMessage('会话已创建')
    })
  }

  async function handleSendQuestion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!selectedSessionId) {
      setError('请先创建或选择会话')
      setMessage('')
      return
    }

    const validationError = validateQuestion(question)
    if (validationError) {
      setError(validationError)
      setMessage('')
      return
    }

    if (startDate && endDate && startDate > endDate) {
      setError('开始日期不能晚于结束日期')
      setMessage('')
      return
    }

    setAnswering(true)
    setError('')
    setMessage('')

    try {
      const normalizedQuestion = question.trim()
      const answer = await chatApi.sendMessage(selectedSessionId, {
        question: normalizedQuestion,
        topK,
        similarityThreshold,
        documentIds: selectedDocumentIds.length > 0 ? selectedDocumentIds : undefined,
        startDate: startDate || undefined,
        endDate: endDate || undefined,
        promptMode,
      })
      setMessages((current) => [
        ...current,
        answer.userMessage,
        answer.assistantMessage,
      ])
      setQuestion('')
      setActiveReference(answer.references[0] ?? null)
      setActiveReferenceQuestion(answer.references[0] ? normalizedQuestion : '')
      setReferenceExpanded(false)
      await loadSessions()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '操作失败')
    } finally {
      setAnswering(false)
    }
  }

  function toggleDocumentSelection(documentId: number) {
    setSelectedDocumentIds((current) =>
      current.includes(documentId)
        ? current.filter((id) => id !== documentId)
        : [...current, documentId],
    )
  }

  return (
    <section className="chat-page">
      <aside className="chat-sidebar">
        <div className="section-heading">
          <h2>问答会话</h2>
          <span>{sessions.length}</span>
        </div>

        <form className="chat-session-form" onSubmit={handleCreateSession}>
          <label>
            知识库
            <select
              value={selectedKnowledgeBaseId ?? ''}
              onChange={(event) =>
                setSelectedKnowledgeBaseId(
                  event.target.value ? Number(event.target.value) : null,
                )
              }
            >
              {knowledgeBases.map((knowledgeBase) => (
                <option key={knowledgeBase.id} value={knowledgeBase.id}>
                  {knowledgeBase.name}
                </option>
              ))}
            </select>
          </label>

          <label>
            会话标题
            <input
              maxLength={200}
              onChange={(event) => setSessionTitle(event.target.value)}
              placeholder="Java 面试问答"
              value={sessionTitle}
            />
          </label>

          <button className="primary-button" disabled={loading} type="submit">
            新建会话
          </button>
        </form>

        <div className="chat-session-list">
          {sessions.map((session) => (
            <button
              className={
                session.id === selectedSessionId
                  ? 'chat-session-item active'
                  : 'chat-session-item'
              }
              key={session.id}
              onClick={() => setSelectedSessionId(session.id)}
              type="button"
            >
              <span>{session.title}</span>
              <small>{formatDateTime(session.updatedAt)}</small>
            </button>
          ))}

          {sessions.length === 0 && (
            <div className="empty-state">暂无会话</div>
          )}
        </div>
      </aside>

      <section className="chat-main">
        <div className="chat-header">
          <div>
            <p className="eyebrow">智能问答</p>
            <h2>{selectedSession?.title ?? '请选择或创建会话'}</h2>
          </div>
          <span>{selectedKnowledgeBase?.name ?? '未选择知识库'}</span>
        </div>

        <StatusMessage error={error} message={message} />

        <div className="message-list" ref={messageListRef}>
          {messages.map((chatMessage, messageIndex) => (
            <article
              className={`message-item ${chatMessage.role.toLowerCase()}`}
              key={chatMessage.id}
            >
              <div className="message-bubble">
                <p>{chatMessage.content}</p>
                <small>{formatDateTime(chatMessage.createdAt)}</small>
              </div>

              {chatMessage.references.length > 0 && (
                <div className="reference-list">
                  {chatMessage.references.map((reference) => (
                    <button
                      aria-pressed={activeReference?.chunkId === reference.chunkId}
                      className={
                        activeReference?.chunkId === reference.chunkId
                          ? 'active'
                          : undefined
                      }
                      key={`${chatMessage.id}-${reference.chunkId}`}
                      onClick={() => {
                        setActiveReference(reference)
                        setActiveReferenceQuestion(questionForMessage(messages, messageIndex))
                        setReferenceExpanded(false)
                      }}
                      type="button"
                    >
                      {referenceLabel(reference)} · {similarityLabel(reference.similarity)}
                    </button>
                  ))}
                </div>
              )}
            </article>
          ))}

          {answering && (
            <article className="message-item assistant">
              <div className="message-bubble pending-answer">
                <p>正在根据知识库生成回答...</p>
              </div>
            </article>
          )}

          {messages.length === 0 && !answering && (
            <div className="empty-detail">
              <h2>暂无消息</h2>
              <p>创建会话后，在下方输入问题开始问答</p>
            </div>
          )}
        </div>

        <form className="question-form" onSubmit={handleSendQuestion}>
          <div className="question-toolbar">
            <label>
              回答模式
              <select
                onChange={(event) => setPromptMode(event.target.value as PromptMode)}
                value={promptMode}
              >
                {PROMPT_MODE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              引用数量
              <select
                onChange={(event) => setTopK(Number(event.target.value))}
                value={topK}
              >
                <option value={3}>3 条</option>
                <option value={5}>5 条</option>
                <option value={8}>8 条</option>
              </select>
            </label>
            <label>
              最低相关度
              <select
                onChange={(event) => setSimilarityThreshold(Number(event.target.value))}
                value={similarityThreshold}
              >
                <option value={0.25}>宽松（25%）</option>
                <option value={0.35}>标准（35%）</option>
                <option value={0.5}>严格（50%）</option>
              </select>
            </label>
          </div>
          <div className="question-filter-row">
            <div className="document-filter" ref={documentFilterRef}>
              <span className="filter-label">检索文档</span>
              <div className="document-filter-control">
                <button
                  aria-expanded={documentFilterOpen}
                  aria-haspopup="listbox"
                  className="document-filter-trigger"
                  disabled={documents.length === 0}
                  onClick={() => setDocumentFilterOpen((current) => !current)}
                  type="button"
                >
                  <span title={documentFilterLabel}>{documentFilterLabel}</span>
                  <span aria-hidden="true" className="document-filter-chevron">⌄</span>
                </button>

                {documentFilterOpen && (
                  <div aria-multiselectable="true" className="document-filter-menu" role="listbox">
                    <div className="document-filter-menu-header">
                      <span>选择要检索的文档</span>
                      <div className="document-filter-menu-actions">
                        <button
                          disabled={selectedDocumentIds.length === documents.length}
                          onClick={() => setSelectedDocumentIds(documents.map((document) => document.id))}
                          type="button"
                        >
                          全选
                        </button>
                        {selectedDocumentIds.length > 0 && (
                          <button onClick={() => setSelectedDocumentIds([])} type="button">
                            清空
                          </button>
                        )}
                      </div>
                    </div>
                    {documents.map((document) => {
                      const selected = selectedDocumentIds.includes(document.id)
                      return (
                        <button
                          aria-selected={selected}
                          className={selected ? 'document-filter-option selected' : 'document-filter-option'}
                          key={document.id}
                          onClick={() => toggleDocumentSelection(document.id)}
                          role="option"
                          type="button"
                        >
                          <span title={document.fileName}>{document.fileName}</span>
                          <span aria-hidden="true" className="document-filter-check">
                            {selected ? '✓' : ''}
                          </span>
                        </button>
                      )
                    })}
                  </div>
                )}
              </div>
            </div>
            <label>
              上传开始日期
              <input
                onChange={(event) => setStartDate(event.target.value)}
                type="date"
                value={startDate}
              />
            </label>
            <label>
              上传结束日期
              <input
                onChange={(event) => setEndDate(event.target.value)}
                type="date"
                value={endDate}
              />
            </label>
          </div>
          <div className="question-input-row">
            <textarea
              maxLength={1000}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="输入一个关于当前知识库的问题"
              value={question}
            />
            <button
              className="primary-button"
              disabled={answering || !selectedSessionId}
              type="submit"
            >
              发送
            </button>
          </div>
        </form>
      </section>

      <aside className="reference-pane">
        <div className="section-heading">
          <h2>引用预览</h2>
        </div>

        {activeReference ? (
          <article className="reference-preview">
            <h3>{referenceLabel(activeReference)}</h3>
            <dl>
              <div>
                <dt>页码</dt>
                <dd>{activeReference.pageNumber ? `第 ${activeReference.pageNumber} 页` : '无'}</dd>
              </div>
              <div>
                <dt>Chunk</dt>
                <dd>{activeReference.chunkIndex}</dd>
              </div>
              <div>
                <dt>相似度</dt>
                <dd>{similarityLabel(activeReference.similarity)}</dd>
              </div>
            </dl>
            <p>
              {highlightedContent(
                referenceExpanded
                  ? activeReference.content
                  : activeReference.content.slice(0, REFERENCE_PREVIEW_LIMIT),
                activeReferenceQuestion,
              )}
              {!referenceExpanded && activeReference.content.length > REFERENCE_PREVIEW_LIMIT ? '…' : ''}
            </p>
            {activeReference.content.length > REFERENCE_PREVIEW_LIMIT && (
              <button
                className="reference-content-toggle"
                onClick={() => setReferenceExpanded((current) => !current)}
                type="button"
              >
                {referenceExpanded ? '收起内容' : '展开全文'}
              </button>
            )}
          </article>
        ) : (
          <div className="empty-state">点击回答下方的引用查看片段</div>
        )}
      </aside>
    </section>
  )
}
