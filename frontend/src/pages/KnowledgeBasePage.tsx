import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, knowledgeBaseApi } from '../api/http'
import { useAuth } from '../auth/useAuth'
import { DocumentPanel } from '../components/DocumentPanel'
import { StatusMessage } from '../components/StatusMessage'
import type { KnowledgeBaseResponse } from '../types/api'
import { formatDateTime } from '../utils/date'

type KnowledgeBaseForm = {
  name: string
  description: string
}

const emptyKnowledgeBaseForm: KnowledgeBaseForm = {
  name: '',
  description: '',
}

function validateKnowledgeBaseForm(form: KnowledgeBaseForm) {
  const name = form.name.trim()

  if (!name) {
    return '知识库名称不能为空'
  }

  if (name.length > 100) {
    return '知识库名称不能超过 100 个字符'
  }

  if (form.description.length > 1000) {
    return '知识库描述不能超过 1000 个字符'
  }

  return ''
}

export function KnowledgeBasePage() {
  const { session } = useAuth()
  const [knowledgeBaseForm, setKnowledgeBaseForm] =
    useState<KnowledgeBaseForm>(emptyKnowledgeBaseForm)
  const [editForm, setEditForm] =
    useState<KnowledgeBaseForm>(emptyKnowledgeBaseForm)
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseResponse[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const selectedKnowledgeBase = useMemo(
    () => knowledgeBases.find((item) => item.id === selectedId) ?? null,
    [knowledgeBases, selectedId],
  )

  useEffect(() => {
    if (!session) {
      setKnowledgeBases([])
      setSelectedId(null)
      return
    }

    void runAction(loadKnowledgeBases)
  }, [session])

  async function runAction(action: () => Promise<void>) {
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
  }

  async function loadKnowledgeBases() {
    const items = await knowledgeBaseApi.list()
    setKnowledgeBases(items)
    setSelectedId((current) => {
      if (current && items.some((item) => item.id === current)) {
        return current
      }
      return items[0]?.id ?? null
    })
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const validationError = validateKnowledgeBaseForm(knowledgeBaseForm)
    if (validationError) {
      setError(validationError)
      setMessage('')
      return
    }

    await runAction(async () => {
      const description = knowledgeBaseForm.description.trim()
      const created = await knowledgeBaseApi.create({
        name: knowledgeBaseForm.name.trim(),
        description: description || undefined,
      })
      setKnowledgeBaseForm(emptyKnowledgeBaseForm)
      await loadKnowledgeBases()
      setSelectedId(created.id)
      setMessage('知识库已创建')
    })
  }

  function startEdit(item: KnowledgeBaseResponse) {
    setEditingId(item.id)
    setEditForm({
      name: item.name,
      description: item.description ?? '',
    })
    setError('')
    setMessage('')
  }

  async function handleUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (editingId === null) {
      return
    }

    const validationError = validateKnowledgeBaseForm(editForm)
    if (validationError) {
      setError(validationError)
      setMessage('')
      return
    }

    await runAction(async () => {
      const description = editForm.description.trim()
      const updated = await knowledgeBaseApi.update(editingId, {
        name: editForm.name.trim(),
        description: description || undefined,
      })
      await loadKnowledgeBases()
      setSelectedId(updated.id)
      setEditingId(null)
      setMessage('知识库已更新')
    })
  }

  async function handleDelete(item: KnowledgeBaseResponse) {
    const confirmed = window.confirm(`确认删除知识库「${item.name}」？`)
    if (!confirmed) {
      return
    }

    await runAction(async () => {
      await knowledgeBaseApi.remove(item.id)
      await loadKnowledgeBases()
      setEditingId(null)
      setMessage('知识库已删除')
    })
  }

  return (
    <section className="workspace-grid">
      <aside className="sidebar">
        <div className="section-heading">
          <h2>我的知识库</h2>
          <span>{knowledgeBases.length}</span>
        </div>

        <form className="kb-form" onSubmit={handleCreate}>
          <label>
            名称
            <input
              value={knowledgeBaseForm.name}
              onChange={(event) =>
                setKnowledgeBaseForm((current) => ({
                  ...current,
                  name: event.target.value,
                }))
              }
              maxLength={100}
              placeholder="Java 学习笔记"
              required
            />
          </label>
          <label>
            描述
            <textarea
              value={knowledgeBaseForm.description}
              onChange={(event) =>
                setKnowledgeBaseForm((current) => ({
                  ...current,
                  description: event.target.value,
                }))
              }
              maxLength={1000}
              placeholder="Spring Boot、JPA、Redis"
            />
          </label>
          <button className="primary-button" disabled={loading} type="submit">
            创建知识库
          </button>
        </form>

        <div className="kb-list">
          {knowledgeBases.map((item) => (
            <button
              key={item.id}
              className={item.id === selectedId ? 'kb-item active' : 'kb-item'}
              type="button"
              onClick={() => setSelectedId(item.id)}
            >
              <span>{item.name}</span>
              <small>{item.documentCount} 个文档</small>
            </button>
          ))}

          {knowledgeBases.length === 0 && (
            <div className="empty-state">暂无知识库</div>
          )}
        </div>
      </aside>

      <section className="detail-pane">
        <StatusMessage error={error} message={message} />

        {selectedKnowledgeBase ? (
          <>
            <div className="detail-header">
              <div>
                <p className="eyebrow">知识库详情</p>
                <h2>{selectedKnowledgeBase.name}</h2>
              </div>
              <div className="detail-actions">
                <button
                  className="ghost-button"
                  type="button"
                  onClick={() => startEdit(selectedKnowledgeBase)}
                >
                  编辑
                </button>
                <button
                  className="danger-button"
                  type="button"
                  onClick={() => void handleDelete(selectedKnowledgeBase)}
                >
                  删除
                </button>
              </div>
            </div>

            <dl className="metadata-grid">
              <div>
                <dt>文档数量</dt>
                <dd>{selectedKnowledgeBase.documentCount}</dd>
              </div>
              <div>
                <dt>创建时间</dt>
                <dd>{formatDateTime(selectedKnowledgeBase.createdAt)}</dd>
              </div>
              <div>
                <dt>更新时间</dt>
                <dd>{formatDateTime(selectedKnowledgeBase.updatedAt)}</dd>
              </div>
            </dl>

            <section className="description-block">
              <h3>描述</h3>
              <p>{selectedKnowledgeBase.description || '未填写描述'}</p>
            </section>

            <DocumentPanel
              knowledgeBaseId={selectedKnowledgeBase.id}
              onKnowledgeBaseChanged={loadKnowledgeBases}
            />

            {editingId === selectedKnowledgeBase.id && (
              <form className="edit-form" onSubmit={handleUpdate}>
                <label>
                  名称
                  <input
                    value={editForm.name}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        name: event.target.value,
                    }))
                  }
                  maxLength={100}
                  required
                />
              </label>
                <label>
                  描述
                  <textarea
                    value={editForm.description}
                    onChange={(event) =>
                      setEditForm((current) => ({
                        ...current,
                        description: event.target.value,
                    }))
                  }
                  maxLength={1000}
                />
                </label>
                <div className="form-actions">
                  <button
                    className="ghost-button"
                    type="button"
                    onClick={() => setEditingId(null)}
                  >
                    取消
                  </button>
                  <button
                    className="primary-button"
                    disabled={loading}
                    type="submit"
                  >
                    保存修改
                  </button>
                </div>
              </form>
            )}
          </>
        ) : (
          <div className="empty-detail">
            <h2>暂无选中的知识库</h2>
            <p>创建后会显示在这里</p>
          </div>
        )}
      </section>
    </section>
  )
}
