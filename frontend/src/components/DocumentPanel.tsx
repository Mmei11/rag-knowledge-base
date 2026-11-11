import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, documentApi } from '../api/http'
import { formatDateTime } from '../utils/date'
import { StatusMessage } from './StatusMessage'
import type { DocumentResponse, DocumentStatus } from '../types/api'

const MAX_FILE_SIZE = 10 * 1024 * 1024
const ALLOWED_EXTENSIONS = ['pdf', 'txt', 'md', 'markdown', 'docx', 'xlsx']

type DocumentPanelProps = {
  knowledgeBaseId: number
  canWrite: boolean
  onKnowledgeBaseChanged: () => Promise<void>
}

function formatFileSize(size: number) {
  if (size < 1024) {
    return `${size} B`
  }

  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }

  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function extensionOf(fileName: string) {
  return fileName.split('.').pop()?.toLowerCase() ?? ''
}

function statusText(status: DocumentStatus) {
  const labels: Record<DocumentStatus, string> = {
    PENDING: '等待解析',
    PROCESSING: '解析中',
    COMPLETED: '已完成',
    FAILED: '解析失败',
  }
  return labels[status]
}

function isProcessing(status: DocumentStatus) {
  return status === 'PENDING' || status === 'PROCESSING'
}

function validateFile(file: File | null) {
  if (!file) {
    return '请选择要上传的文件'
  }

  const extension = extensionOf(file.name)
  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    return '仅支持 PDF、TXT、Markdown、Word、Excel 文件'
  }

  if (file.size > MAX_FILE_SIZE) {
    return '文件大小不能超过 10MB'
  }

  return ''
}

export function DocumentPanel({
  knowledgeBaseId,
  canWrite,
  onKnowledgeBaseChanged,
}: DocumentPanelProps) {
  const [documents, setDocuments] = useState<DocumentResponse[]>([])
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [loading, setLoading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState<number | null>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const processingDocumentIds = useMemo(
    () =>
      documents
        .filter((document) => isProcessing(document.status))
        .map((document) => document.id),
    [documents],
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

  const loadDocuments = useCallback(async () => {
    const items = await documentApi.listByKnowledgeBase(knowledgeBaseId)
    setDocuments(items)
  }, [knowledgeBaseId])

  const refreshProcessingStatuses = useCallback(async (documentIds: number[]) => {
    const statusResults = await Promise.all(
      documentIds.map(async (documentId) => {
        try {
          return await documentApi.getStatus(documentId)
        } catch (err) {
          if (err instanceof ApiError && err.status === 404) {
            return { id: documentId, missing: true } as const
          }
          throw err
        }
      }),
    )
    const missingDocumentIds = new Set(
      statusResults
        .filter((result): result is { id: number; missing: true } => 'missing' in result)
        .map((result) => result.id),
    )
    const statuses = statusResults.filter(
      (result): result is Exclude<(typeof statusResults)[number], { missing: true }> =>
        !('missing' in result),
    )
    setDocuments((current) =>
      current.filter((document) => !missingDocumentIds.has(document.id)).map((document) => {
        const latest = statuses.find((status) => status.id === document.id)
        if (!latest) {
          return document
        }

        return {
          ...document,
          status: latest.status,
          parseProgress: latest.parseProgress,
          parseStage: latest.parseStage,
          errorMessage: latest.errorMessage,
          updatedAt: latest.updatedAt,
        }
      }),
    )
  }, [])

  useEffect(() => {
    void runAction(loadDocuments)
  }, [loadDocuments, runAction])

  useEffect(() => {
    if (processingDocumentIds.length === 0) {
      return
    }

    const timer = window.setInterval(() => {
      void refreshProcessingStatuses(processingDocumentIds)
    }, 1000)

    return () => window.clearInterval(timer)
  }, [processingDocumentIds, refreshProcessingStatuses])

  useEffect(() => {
    const shouldDismissUploadedNotice =
      message === '文档已上传，正在解析' && processingDocumentIds.length === 0
    const shouldDismissDeletedNotice = message === '文档已删除'
    if (!shouldDismissUploadedNotice && !shouldDismissDeletedNotice) {
      return
    }

    const timer = window.setTimeout(() => setMessage(''), 3000)
    return () => window.clearTimeout(timer)
  }, [message, processingDocumentIds.length])

  async function handleUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const file = selectedFile
    const validationError = validateFile(file)
    if (validationError) {
      setError(validationError)
      setMessage('')
      return
    }
    if (!file) {
      return
    }

    const form = event.currentTarget
    await runAction(async () => {
      setUploadProgress(0)
      await documentApi.upload(knowledgeBaseId, file, setUploadProgress)
      setSelectedFile(null)
      form.reset()
      await loadDocuments()
      await onKnowledgeBaseChanged()
      setUploadProgress(null)
      setMessage('文档已上传，正在解析')
    })
  }

  async function handleReprocess(document: DocumentResponse) {
    await runAction(async () => {
      await documentApi.reprocess(document.id)
      await loadDocuments()
      setMessage('已重新提交解析任务')
    })
  }

  async function handleDelete(document: DocumentResponse) {
    const confirmed = window.confirm(`确认删除文档「${document.fileName}」？`)
    if (!confirmed) {
      return
    }

    await runAction(async () => {
      await documentApi.remove(document.id)
      await loadDocuments()
      await onKnowledgeBaseChanged()
      setMessage('文档已删除')
    })
  }

  return (
    <section className="document-section">
      <div className="section-heading">
        <h2>文档管理</h2>
        <span>{documents.length}</span>
      </div>

      {canWrite && (
        <form className="upload-form" onSubmit={handleUpload}>
          <label>
            上传文档
            <input
              accept=".pdf,.txt,.md,.markdown,.docx,.xlsx"
              className="file-input"
              onChange={(event) => setSelectedFile(event.target.files?.[0] ?? null)}
              type="file"
            />
          </label>
          <button className="primary-button" disabled={loading} type="submit">
            上传并解析
          </button>
        </form>
      )}

      {uploadProgress !== null && (
        <div className="upload-progress" aria-label="上传进度">
          <span style={{ width: `${uploadProgress}%` }} />
        </div>
      )}

      <StatusMessage error={error} message={message} />

      <div className="document-list">
        {documents.map((document) => (
          <article className="document-item" key={document.id}>
            <div className="document-main">
              <div>
                <h3>{document.fileName}</h3>
                <p className="document-meta">
                  {document.fileType.toUpperCase()} · {formatFileSize(document.fileSize)} ·{' '}
                  {formatDateTime(document.updatedAt)}
                </p>
                {document.errorMessage && (
                  <p className="document-error">{document.errorMessage}</p>
                )}
              </div>
              <span className={`document-status ${document.status.toLowerCase()}`}>
                {isProcessing(document.status)
                  ? `${statusText(document.status)} · ${document.parseProgress}%`
                  : statusText(document.status)}
              </span>
            </div>

            {isProcessing(document.status) && (
              <div className="document-parse-progress">
                <div className="document-parse-progress-label">
                  <span>{document.parseStage ?? statusText(document.status)}</span>
                  <strong>{document.parseProgress}%</strong>
                </div>
                <div
                  aria-label={`${document.fileName} 解析进度`}
                  aria-valuemax={100}
                  aria-valuemin={0}
                  aria-valuenow={document.parseProgress}
                  className="document-parse-progress-track"
                  role="progressbar"
                >
                  <span style={{ width: `${Math.max(document.parseProgress, 2)}%` }} />
                </div>
              </div>
            )}

            {canWrite && (
              <div className="document-actions">
                {!isProcessing(document.status) && (
                  <button
                    className="ghost-button"
                    disabled={loading}
                    onClick={() => void handleReprocess(document)}
                    type="button"
                  >
                    重新解析
                  </button>
                )}
                <button
                  className="danger-button"
                  disabled={loading}
                  onClick={() => void handleDelete(document)}
                  type="button"
                >
                  删除
                </button>
              </div>
            )}
          </article>
        ))}

        {documents.length === 0 && (
          <div className="empty-state">暂无文档</div>
        )}
      </div>
    </section>
  )
}
