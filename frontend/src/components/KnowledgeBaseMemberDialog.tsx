import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, knowledgeBaseMemberApi } from '../api/http'
import { formatDateTime } from '../utils/date'
import { StatusMessage } from './StatusMessage'
import type {
  KnowledgeBaseMemberResponse,
  KnowledgeBaseMemberRole,
} from '../types/api'

type KnowledgeBaseMemberDialogProps = {
  knowledgeBaseId: number
  open: boolean
  onClose: () => void
  onMembersChanged: () => Promise<void>
}

const MEMBER_ROLES: KnowledgeBaseMemberRole[] = ['EDITOR', 'VIEWER']

const memberRoleLabels: Record<KnowledgeBaseMemberRole, string> = {
  EDITOR: '可编辑',
  VIEWER: '只读',
}

function memberErrorText(err: unknown) {
  if (!(err instanceof ApiError)) {
    return '操作失败'
  }

  const fallbacks: Record<number, string> = {
    400: '无法添加该用户',
    403: '只有知识库所有者可以管理成员',
    404: '用户不存在',
    409: '该用户已经是知识库成员',
  }

  return err.message || fallbacks[err.status] || '操作失败'
}

export function KnowledgeBaseMemberDialog({
  knowledgeBaseId,
  open,
  onClose,
  onMembersChanged,
}: KnowledgeBaseMemberDialogProps) {
  const [members, setMembers] = useState<KnowledgeBaseMemberResponse[]>([])
  const [username, setUsername] = useState('')
  const [newRole, setNewRole] = useState<KnowledgeBaseMemberRole>('VIEWER')
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  const runAction = useCallback(async (action: () => Promise<void>) => {
    setLoading(true)
    setError('')
    setMessage('')

    try {
      await action()
    } catch (err) {
      setError(memberErrorText(err))
    } finally {
      setLoading(false)
    }
  }, [])

  const loadMembers = useCallback(async () => {
    const items = await knowledgeBaseMemberApi.list(knowledgeBaseId)
    setMembers(items)
  }, [knowledgeBaseId])

  useEffect(() => {
    if (!open) {
      return
    }

    setMessage('')
    void runAction(loadMembers)
  }, [open, loadMembers, runAction])

  async function handleAdd(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const name = username.trim()
    if (!name) {
      setError('请输入要添加的用户名')
      setMessage('')
      return
    }

    await runAction(async () => {
      await knowledgeBaseMemberApi.add(knowledgeBaseId, {
        username: name,
        role: newRole,
      })
      setUsername('')
      setNewRole('VIEWER')
      await loadMembers()
      await onMembersChanged()
      setMessage('成员已添加')
    })
  }

  async function handleRoleChange(
    member: KnowledgeBaseMemberResponse,
    role: KnowledgeBaseMemberRole,
  ) {
    if (role === member.role) {
      return
    }

    await runAction(async () => {
      await knowledgeBaseMemberApi.updateRole(knowledgeBaseId, member.userId, {
        role,
      })
      await loadMembers()
      await onMembersChanged()
      setMessage(`已将 ${member.username} 的角色改为${memberRoleLabels[role]}`)
    })
  }

  async function handleRemove(member: KnowledgeBaseMemberResponse) {
    const confirmed = window.confirm(
      `确认将「${member.username}」从该知识库移除？`,
    )
    if (!confirmed) {
      return
    }

    await runAction(async () => {
      await knowledgeBaseMemberApi.remove(knowledgeBaseId, member.userId)
      await loadMembers()
      await onMembersChanged()
      setMessage('成员已移除')
    })
  }

  if (!open) {
    return null
  }

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div
        aria-labelledby="kb-member-dialog-title"
        aria-modal="true"
        className="dialog"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
      >
        <div className="dialog-header">
          <div>
            <p className="eyebrow">知识库权限</p>
            <h2 id="kb-member-dialog-title">成员管理</h2>
          </div>
          <button className="ghost-button" type="button" onClick={onClose}>
            关闭
          </button>
        </div>

        <form className="member-add-form" onSubmit={handleAdd}>
          <label>
            用户名
            <input
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="输入已注册用户名"
              required
            />
          </label>
          <label>
            角色
            <select
              value={newRole}
              onChange={(event) =>
                setNewRole(event.target.value as KnowledgeBaseMemberRole)
              }
            >
              {MEMBER_ROLES.map((role) => (
                <option key={role} value={role}>
                  {memberRoleLabels[role]}
                </option>
              ))}
            </select>
          </label>
          <button className="primary-button" disabled={loading} type="submit">
            添加成员
          </button>
        </form>

        <StatusMessage error={error} message={message} />

        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>用户名</th>
                <th>昵称</th>
                <th>角色</th>
                <th>加入时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {members.map((member) => (
                <tr key={member.userId}>
                  <td>{member.username}</td>
                  <td>{member.nickname || '-'}</td>
                  <td>
                    <select
                      aria-label={`${member.username} 的角色`}
                      className="inline-select"
                      disabled={loading}
                      value={member.role}
                      onChange={(event) =>
                        void handleRoleChange(
                          member,
                          event.target.value as KnowledgeBaseMemberRole,
                        )
                      }
                    >
                      {MEMBER_ROLES.map((role) => (
                        <option key={role} value={role}>
                          {memberRoleLabels[role]}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>{formatDateTime(member.createdAt)}</td>
                  <td>
                    <button
                      className="danger-button"
                      disabled={loading}
                      type="button"
                      onClick={() => void handleRemove(member)}
                    >
                      移除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {members.length === 0 && (
            <div className="empty-state">暂无共享成员</div>
          )}
        </div>
      </div>
    </div>
  )
}
