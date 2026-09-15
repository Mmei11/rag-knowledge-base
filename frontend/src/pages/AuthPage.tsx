import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, authApi } from '../api/http'
import { useAuth } from '../auth/useAuth'
import { StatusMessage } from '../components/StatusMessage'

type AuthMode = 'login' | 'register'

type AuthForm = {
  username: string
  password: string
  nickname: string
}

const emptyAuthForm: AuthForm = {
  username: '',
  password: '',
  nickname: '',
}

function validateAuthForm(authMode: AuthMode, form: AuthForm) {
  const username = form.username.trim()
  const password = form.password
  const nickname = form.nickname.trim()

  if (username.length < 3 || username.length > 50) {
    return '用户名长度必须在 3 到 50 个字符之间'
  }

  if (!/^[a-zA-Z0-9_]+$/.test(username)) {
    return '用户名只能包含字母、数字和下划线'
  }

  if (password.length < 8 || password.length > 72) {
    return '密码长度必须在 8 到 72 个字符之间'
  }

  if (authMode === 'register' && nickname.length > 100) {
    return '昵称不能超过 100 个字符'
  }

  return ''
}

export function AuthPage() {
  const { signIn } = useAuth()
  const [authMode, setAuthMode] = useState<AuthMode>('login')
  const [authForm, setAuthForm] = useState<AuthForm>(emptyAuthForm)
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  function handleModeChange(nextMode: AuthMode) {
    if (nextMode === authMode) {
      return
    }

    setAuthMode(nextMode)
    setAuthForm(emptyAuthForm)
    setMessage('')
    setError('')
  }

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

  async function handleAuthSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const validationError = validateAuthForm(authMode, authForm)
    if (validationError) {
      setError(validationError)
      setMessage('')
      return
    }

    await runAction(async () => {
      const username = authForm.username.trim()
      const nickname = authForm.nickname.trim()

      if (authMode === 'register') {
        await authApi.register({
          username,
          password: authForm.password,
          nickname: nickname || undefined,
        })
        setAuthMode('login')
        setAuthForm((current) => ({
          ...current,
          username: current.username.trim(),
          password: '',
          nickname: '',
        }))
        setMessage('注册成功，请登录')
        return
      }

      const login = await authApi.login({
        username,
        password: authForm.password,
      })
      signIn(login)
      setAuthForm(emptyAuthForm)
    })
  }

  return (
    <main className="app auth-shell">
      <section className="auth-panel">
        <div className="auth-card-header">
          <div className="brand-block">
            <span className="brand-mark">R</span>
            <div>
              <p className="auth-kicker">RAG Knowledge Base</p>
            </div>
          </div>
          <div className="auth-copy">
            <h1>智能知识库</h1>
            <p>{authMode === 'login' ? '欢迎回来，继续管理你的知识空间。' : '创建账号，开始构建你的个人知识库。'}</p>
          </div>
        </div>

        <div className="mode-tabs" role="tablist" aria-label="认证模式">
          <button
            type="button"
            className={authMode === 'login' ? 'active' : ''}
            onClick={() => handleModeChange('login')}
            aria-selected={authMode === 'login'}
          >
            登录
          </button>
          <button
            type="button"
            className={authMode === 'register' ? 'active' : ''}
            onClick={() => handleModeChange('register')}
            aria-selected={authMode === 'register'}
          >
            注册
          </button>
        </div>

        <form className="auth-form" onSubmit={handleAuthSubmit}>
          <label>
            用户名
            <input
              value={authForm.username}
              onChange={(event) =>
                setAuthForm((current) => ({
                  ...current,
                  username: event.target.value,
                }))
              }
              autoComplete="username"
              minLength={3}
              maxLength={50}
              pattern="[A-Za-z0-9_]+"
              placeholder="输入用户名"
              required
            />
          </label>

          <label>
            密码
            <input
              value={authForm.password}
              onChange={(event) =>
                setAuthForm((current) => ({
                  ...current,
                  password: event.target.value,
                }))
              }
              type="password"
              autoComplete={authMode === 'login' ? 'current-password' : 'new-password'}
              minLength={8}
              maxLength={72}
              placeholder="输入密码"
              required
            />
          </label>

          {authMode === 'register' && (
            <label>
              昵称
              <input
                value={authForm.nickname}
                onChange={(event) =>
                  setAuthForm((current) => ({
                    ...current,
                    nickname: event.target.value,
                  }))
                }
                maxLength={100}
                placeholder="输入昵称"
              />
            </label>
          )}

          <button className="primary-button" disabled={loading} type="submit">
            {loading ? '处理中' : authMode === 'login' ? '登录' : '注册'}
          </button>
        </form>

        <StatusMessage error={error} message={message} />
      </section>
    </main>
  )
}
