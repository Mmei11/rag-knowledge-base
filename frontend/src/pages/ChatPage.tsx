import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, chatApi, knowledgeBaseApi } from '../api/http'
import { StatusMessage } from '../components/StatusMessage'
import type {
  ChatMessageResponse,
  ChatReferenceResponse,
  ChatSessionResponse,
  KnowledgeBaseResponse,
} from '../types/api'
import { formatDateTime } from '../utils/date'

const DEFAULT_TOP_K = 5

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
  return `文档 ${reference.documentId} · Chunk ${reference.chunkIndex}`
}

export function ChatPage() {
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseResponse[]>([])
  const [sessions, setSessions] = useState<ChatSessionResponse[]>([])
  const [messages, setMessages] = useState<ChatMessageResponse[]>([])
  const [selectedKnowledgeBaseId, setSelectedKnowledgeBaseId] = useState<number | null>(null)
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null)
  const [sessionTitle, setSessionTitle] = useState('')
  const [question, setQuestion] = useState('')
  const [activeReference, setActiveReference] = useState<ChatReferenceResponse | null>(null)
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

  const loadMessages = useCallback(async (sessionId: number) => {
    const items = await chatApi.listMessages(sessionId)
    setMessages(items)
    setActiveReference(null)
  }, [])

  useEffect(() => {
    void runAction(async () => {
      await Promise.all([loadKnowledgeBases(), loadSessions()])
    })
  }, [loadKnowledgeBases, loadSessions, runAction])

  useEffect(() => {
    if (!selectedSessionId) {
      setMessages([])
      return
    }

    void runAction(() => loadMessages(selectedSessionId))
  }, [loadMessages, runAction, selectedSessionId])

  useEffect(() => {
    if (selectedSession) {
      setSelectedKnowledgeBaseId(selectedSession.knowledgeBaseId)
    }
  }, [selectedSession])

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

    setAnswering(true)
    setError('')
    setMessage('')

    try {
      const answer = await chatApi.sendMessage(selectedSessionId, {
        question: question.trim(),
        topK: DEFAULT_TOP_K,
      })
      setMessages((current) => [
        ...current,
        answer.userMessage,
        answer.assistantMessage,
      ])
      setQuestion('')
      setActiveReference(answer.references[0] ?? null)
      await loadSessions()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '操作失败')
    } finally {
      setAnswering(false)
    }
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

        <div className="message-list">
          {messages.map((chatMessage) => (
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
                      key={`${chatMessage.id}-${reference.chunkId}`}
                      onClick={() => setActiveReference(reference)}
                      type="button"
                    >
                      {referenceLabel(reference)}
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
        </form>
      </section>

      <aside className="reference-pane">
        <div className="section-heading">
          <h2>引用预览</h2>
        </div>

        {activeReference ? (
          <article className="reference-preview">
            <dl>
              <div>
                <dt>文档 ID</dt>
                <dd>{activeReference.documentId}</dd>
              </div>
              <div>
                <dt>Chunk</dt>
                <dd>{activeReference.chunkIndex}</dd>
              </div>
              <div>
                <dt>相似度</dt>
                <dd>{activeReference.similarity.toFixed(4)}</dd>
              </div>
            </dl>
            <p>{activeReference.content}</p>
          </article>
        ) : (
          <div className="empty-state">点击回答下方的引用查看片段</div>
        )}
      </aside>
    </section>
  )
}
