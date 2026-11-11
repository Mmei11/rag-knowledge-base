import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, adminApi } from '../api/http'
import { useAuth } from '../auth/useAuth'
import { StatusMessage } from '../components/StatusMessage'
import { formatDateTime } from '../utils/date'
import type {
  OperationAction,
  OperationLogResponse,
  OperationResourceType,
  PageResponse,
  UserResponse,
  UserRole,
  UserStatus,
} from '../types/api'

type AdminTab = 'users' | 'logs'

type UserFilters = {
  keyword: string
  status: UserStatus | ''
  role: UserRole | ''
}

type LogFilters = {
  userId: string
  action: OperationAction | ''
  resourceType: OperationResourceType | ''
  resourceId: string
  from: string
  to: string
}

const emptyUserFilters: UserFilters = {
  keyword: '',
  status: '',
  role: '',
}

const emptyLogFilters: LogFilters = {
  userId: '',
  action: '',
  resourceType: '',
  resourceId: '',
  from: '',
  to: '',
}

const userRoleLabels: Record<UserRole, string> = {
  SUPER_ADMIN: '主管理员',
  ADMIN: '子管理员',
  USER: '普通用户',
}

const assignableRoles: UserRole[] = ['USER', 'ADMIN']

const userStatusLabels: Record<UserStatus, string> = {
  ENABLED: '启用',
  DISABLED: '禁用',
}

const actionLabels: Record<OperationAction, string> = {
  USER_REGISTER: '用户注册',
  USER_LOGIN: '登录',
  USER_ENABLE: '启用用户',
  USER_DISABLE: '禁用用户',
  USER_ROLE_CHANGE: '修改用户角色',
  KB_CREATE: '创建知识库',
  KB_UPDATE: '修改知识库',
  KB_DELETE: '删除知识库',
  KB_MEMBER_ADD: '添加知识库成员',
  KB_MEMBER_ROLE_CHANGE: '修改成员角色',
  KB_MEMBER_REMOVE: '移除知识库成员',
  DOCUMENT_UPLOAD: '上传文档',
  DOCUMENT_DELETE: '删除文档',
  DOCUMENT_REPROCESS: '重新解析文档',
}

const operationActions = Object.keys(actionLabels) as OperationAction[]

const resourceTypeLabels: Record<OperationResourceType, string> = {
  USER: '用户',
  KNOWLEDGE_BASE: '知识库',
  DOCUMENT: '文档',
}

const operationResourceTypes = Object.keys(
  resourceTypeLabels,
) as OperationResourceType[]

function parseOptionalNumber(value: string) {
  const trimmed = value.trim()
  if (!trimmed) {
    return undefined
  }
  const parsed = Number(trimmed)
  return Number.isFinite(parsed) ? parsed : undefined
}

function roleBadgeClass(role: UserRole) {
  return role.toLowerCase().replace('_', '-')
}

function errorText(err: unknown) {
  return err instanceof ApiError ? err.message : '操作失败'
}

export function AdminPage() {
  const { session } = useAuth()
  const currentUser = session?.user ?? null
  const [activeTab, setActiveTab] = useState<AdminTab>('users')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [pendingRoleChange, setPendingRoleChange] = useState<{
    user: UserResponse
    role: UserRole
  } | null>(null)

  const [userDraft, setUserDraft] = useState<UserFilters>(emptyUserFilters)
  const [userFilters, setUserFilters] = useState<UserFilters>(emptyUserFilters)
  const [userPage, setUserPage] = useState(1)
  const [userData, setUserData] = useState<PageResponse<UserResponse> | null>(
    null,
  )

  const [logDraft, setLogDraft] = useState<LogFilters>(emptyLogFilters)
  const [logFilters, setLogFilters] = useState<LogFilters>(emptyLogFilters)
  const [logPage, setLogPage] = useState(1)
  const [logData, setLogData] = useState<
    PageResponse<OperationLogResponse> | null
  >(null)

  const runAction = useCallback(async (action: () => Promise<void>) => {
    setLoading(true)
    setError('')
    setMessage('')

    try {
      await action()
    } catch (err) {
      setError(errorText(err))
    } finally {
      setLoading(false)
    }
  }, [])

  const loadUsers = useCallback(async () => {
    const data = await adminApi.listUsers({
      keyword: userFilters.keyword.trim() || undefined,
      status: userFilters.status || undefined,
      role: userFilters.role || undefined,
      page: userPage,
      size: 20,
    })
    setUserData(data)
  }, [userFilters, userPage])

  const loadLogs = useCallback(async () => {
    const data = await adminApi.listOperationLogs({
      userId: parseOptionalNumber(logFilters.userId),
      action: logFilters.action || undefined,
      resourceType: logFilters.resourceType || undefined,
      resourceId: parseOptionalNumber(logFilters.resourceId),
      from: logFilters.from || undefined,
      to: logFilters.to || undefined,
      page: logPage,
      size: 20,
    })
    setLogData(data)
  }, [logFilters, logPage])

  useEffect(() => {
    if (activeTab === 'users') {
      void runAction(loadUsers)
    }
  }, [activeTab, loadUsers, runAction])

  useEffect(() => {
    if (activeTab === 'logs') {
      void runAction(loadLogs)
    }
  }, [activeTab, loadLogs, runAction])

  function handleUserFilterSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setUserPage(1)
    setUserFilters({ ...userDraft })
  }

  function handleLogFilterSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setLogPage(1)
    setLogFilters({ ...logDraft })
  }

  function isSelf(user: UserResponse) {
    return currentUser?.id === user.id
  }

  function canChangeRole(user: UserResponse) {
    if (currentUser?.role !== 'SUPER_ADMIN' || isSelf(user)) {
      return false
    }
    return user.role !== 'SUPER_ADMIN'
  }

  function canToggleStatus(user: UserResponse) {
    if (!currentUser || isSelf(user)) {
      return false
    }
    if (currentUser.role === 'SUPER_ADMIN') {
      return user.role !== 'SUPER_ADMIN'
    }
    return currentUser.role === 'ADMIN' && user.role === 'USER'
  }

  async function handleToggleUserStatus(user: UserResponse) {
    const nextStatus: UserStatus =
      user.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
    const verb = nextStatus === 'DISABLED' ? '禁用' : '启用'
    const confirmed = window.confirm(
      `确认${verb}用户「${user.username}」？`,
    )
    if (!confirmed) {
      return
    }

    await runAction(async () => {
      const updated = await adminApi.updateUserStatus(user.id, nextStatus)
      setUserData((current) =>
        current
          ? {
              ...current,
              items: current.items.map((item) =>
                item.id === updated.id ? updated : item,
              ),
            }
          : current,
      )
      setMessage(`已${verb}用户 ${updated.username}`)
    })
  }

  function requestRoleChange(user: UserResponse, role: UserRole) {
    if (role === user.role) {
      return
    }
    setPendingRoleChange({ user, role })
  }

  async function confirmRoleChange() {
    if (!pendingRoleChange) {
      return
    }

    const { user, role } = pendingRoleChange
    await runAction(async () => {
      const updated = await adminApi.updateUserRole(user.id, role)
      setUserData((current) =>
        current
          ? {
              ...current,
              items: current.items.map((item) =>
                item.id === updated.id ? updated : item,
              ),
            }
          : current,
      )
      setMessage(
        `已将 ${updated.username} 的角色改为${userRoleLabels[updated.role]}`,
      )
    })
    setPendingRoleChange(null)
  }

  function switchTab(tab: AdminTab) {
    setActiveTab(tab)
    setError('')
    setMessage('')
  }

  return (
    <section className="admin-page">
      <div className="admin-heading">
        <div>
          <p className="eyebrow">系统管理</p>
          <h2>管理后台</h2>
        </div>
        <div className="admin-tabs" role="tablist">
          <button
            aria-selected={activeTab === 'users'}
            className={activeTab === 'users' ? 'active' : ''}
            role="tab"
            type="button"
            onClick={() => switchTab('users')}
          >
            用户管理
          </button>
          <button
            aria-selected={activeTab === 'logs'}
            className={activeTab === 'logs' ? 'active' : ''}
            role="tab"
            type="button"
            onClick={() => switchTab('logs')}
          >
            操作审计
          </button>
        </div>
      </div>

      <StatusMessage error={error} message={message} />

      {activeTab === 'users' ? (
        <div className="admin-panel">
          <form className="filter-bar" onSubmit={handleUserFilterSubmit}>
            <label>
              关键字
              <input
                value={userDraft.keyword}
                onChange={(event) =>
                  setUserDraft((current) => ({
                    ...current,
                    keyword: event.target.value,
                  }))
                }
                placeholder="用户名或昵称"
              />
            </label>
            <label>
              状态
              <select
                value={userDraft.status}
                onChange={(event) =>
                  setUserDraft((current) => ({
                    ...current,
                    status: event.target.value as UserStatus | '',
                  }))
                }
              >
                <option value="">全部</option>
                <option value="ENABLED">启用</option>
                <option value="DISABLED">禁用</option>
              </select>
            </label>
            <label>
              角色
              <select
                value={userDraft.role}
                onChange={(event) =>
                  setUserDraft((current) => ({
                    ...current,
                    role: event.target.value as UserRole | '',
                  }))
                }
              >
                <option value="">全部</option>
                <option value="USER">普通用户</option>
                <option value="ADMIN">子管理员</option>
                <option value="SUPER_ADMIN">主管理员</option>
              </select>
            </label>
            <button className="primary-button" disabled={loading} type="submit">
              查询
            </button>
          </form>

          <div className="table-scroll">
            <table className="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>用户名</th>
                  <th>昵称</th>
                  <th>角色</th>
                  <th>状态</th>
                  <th>创建时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {(userData?.items ?? []).map((user) => (
                  <tr key={user.id}>
                    <td>{user.id}</td>
                    <td>{user.username}</td>
                    <td>{user.nickname || '-'}</td>
                    <td>
                      {canChangeRole(user) ? (
                        <select
                          aria-label={`${user.username} 的角色`}
                          className="inline-select"
                          disabled={loading}
                          value={user.role}
                          onChange={(event) =>
                            requestRoleChange(
                              user,
                              event.target.value as UserRole,
                            )
                          }
                        >
                          {assignableRoles.map((roleOption) => (
                            <option key={roleOption} value={roleOption}>
                              {userRoleLabels[roleOption]}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <span
                          className={`role-badge ${roleBadgeClass(user.role)}`}
                        >
                          {userRoleLabels[user.role]}
                        </span>
                      )}
                    </td>
                    <td>
                      <span
                        className={`status-badge ${user.status.toLowerCase()}`}
                      >
                        {userStatusLabels[user.status]}
                      </span>
                    </td>
                    <td>{formatDateTime(user.createdAt)}</td>
                    <td>
                      <button
                        className={
                          user.status === 'ENABLED'
                            ? 'danger-button'
                            : 'ghost-button'
                        }
                        disabled={loading || !canToggleStatus(user)}
                        type="button"
                        onClick={() => void handleToggleUserStatus(user)}
                      >
                        {user.status === 'ENABLED' ? '禁用' : '启用'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {userData && userData.items.length === 0 && (
              <div className="empty-state">没有符合条件的用户</div>
            )}
          </div>

          <Pagination
            data={userData}
            loading={loading}
            onChange={setUserPage}
          />
        </div>
      ) : (
        <div className="admin-panel">
          <form className="filter-bar log-filter" onSubmit={handleLogFilterSubmit}>
            <label>
              用户 ID
              <input
                min={1}
                value={logDraft.userId}
                onChange={(event) =>
                  setLogDraft((current) => ({
                    ...current,
                    userId: event.target.value,
                  }))
                }
                placeholder="可选"
                type="number"
              />
            </label>
            <label>
              动作
              <select
                value={logDraft.action}
                onChange={(event) =>
                  setLogDraft((current) => ({
                    ...current,
                    action: event.target.value as OperationAction | '',
                  }))
                }
              >
                <option value="">全部</option>
                {operationActions.map((action) => (
                  <option key={action} value={action}>
                    {actionLabels[action]}
                  </option>
                ))}
              </select>
            </label>
            <label>
              资源类型
              <select
                value={logDraft.resourceType}
                onChange={(event) =>
                  setLogDraft((current) => ({
                    ...current,
                    resourceType: event.target
                      .value as OperationResourceType | '',
                  }))
                }
              >
                <option value="">全部</option>
                {operationResourceTypes.map((type) => (
                  <option key={type} value={type}>
                    {resourceTypeLabels[type]}
                  </option>
                ))}
              </select>
            </label>
            <label>
              资源 ID
              <input
                min={1}
                value={logDraft.resourceId}
                onChange={(event) =>
                  setLogDraft((current) => ({
                    ...current,
                    resourceId: event.target.value,
                  }))
                }
                placeholder="可选"
                type="number"
              />
            </label>
            <label>
              开始日期
              <input
                value={logDraft.from}
                onChange={(event) =>
                  setLogDraft((current) => ({
                    ...current,
                    from: event.target.value,
                  }))
                }
                type="date"
              />
            </label>
            <label>
              结束日期
              <input
                value={logDraft.to}
                onChange={(event) =>
                  setLogDraft((current) => ({
                    ...current,
                    to: event.target.value,
                  }))
                }
                type="date"
              />
            </label>
            <button className="primary-button" disabled={loading} type="submit">
              查询
            </button>
          </form>

          <div className="table-scroll">
            <table className="data-table">
              <thead>
                <tr>
                  <th>时间</th>
                  <th>用户</th>
                  <th>动作</th>
                  <th>资源类型</th>
                  <th>资源 ID</th>
                  <th>IP</th>
                </tr>
              </thead>
              <tbody>
                {(logData?.items ?? []).map((log) => (
                  <tr key={log.id}>
                    <td>{formatDateTime(log.createdAt)}</td>
                    <td>
                      {log.username ?? '-'}
                      {log.userId !== null && ` (#${log.userId})`}
                    </td>
                    <td>{actionLabels[log.action]}</td>
                    <td>
                      {log.resourceType
                        ? resourceTypeLabels[log.resourceType]
                        : '-'}
                    </td>
                    <td>{log.resourceId ?? '-'}</td>
                    <td>{log.ip || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            {logData && logData.items.length === 0 && (
              <div className="empty-state">没有符合条件的操作日志</div>
            )}
          </div>

          <Pagination data={logData} loading={loading} onChange={setLogPage} />
        </div>
      )}

      {pendingRoleChange && (
        <div
          className="dialog-backdrop"
          onClick={() => setPendingRoleChange(null)}
        >
          <div
            aria-labelledby="role-change-dialog-title"
            aria-modal="true"
            className="dialog confirm-dialog"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
          >
            <div className="dialog-header">
              <h2 id="role-change-dialog-title">确认修改角色</h2>
            </div>
            <p className="confirm-text">
              确认将「{pendingRoleChange.user.username}」的角色从「
              {userRoleLabels[pendingRoleChange.user.role]}」改为「
              {userRoleLabels[pendingRoleChange.role]}」？
            </p>
            <div className="form-actions">
              <button
                className="ghost-button"
                type="button"
                onClick={() => setPendingRoleChange(null)}
              >
                取消
              </button>
              <button
                className="primary-button"
                disabled={loading}
                type="button"
                onClick={() => void confirmRoleChange()}
              >
                确认修改
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}

function Pagination({
  data,
  loading,
  onChange,
}: {
  data: PageResponse<unknown> | null
  loading: boolean
  onChange: (page: number) => void
}) {
  const page = data?.page ?? 1
  const totalPages = data?.totalPages ?? 0

  return (
    <div className="pagination">
      <button
        className="ghost-button"
        disabled={loading || page <= 1}
        type="button"
        onClick={() => onChange(page - 1)}
      >
        上一页
      </button>
      <span>
        第 {page} / {Math.max(totalPages, 1)} 页 · 共 {data?.totalElements ?? 0} 条
      </span>
      <button
        className="ghost-button"
        disabled={loading || totalPages === 0 || page >= totalPages}
        type="button"
        onClick={() => onChange(page + 1)}
      >
        下一页
      </button>
    </div>
  )
}
