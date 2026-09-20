import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError, authApi } from '../api/http'
import { useAuth } from '../auth/useAuth'
import { StatusMessage } from '../components/StatusMessage'

type AuthMode = 'login' | 'register'
type Language = 'zh-CN' | 'en'

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

const copy = {
  'zh-CN': {
    brand: 'RAG KNOWLEDGE BASE',
    headline: '把知识，变成答案。',
    tagline: '你的专属 AI 知识空间',
    welcome: '欢迎回来',
    createAccount: '创建你的账号',
    loginSubtitle: '登录你的知识空间',
    registerSubtitle: '开始构建属于你的知识库',
    username: '用户名',
    usernamePlaceholder: '请输入用户名',
    password: '密码',
    passwordPlaceholder: '请输入密码',
    nickname: '昵称',
    nicknamePlaceholder: '选填',
    login: '登录',
    register: '注册',
    processing: '处理中…',
    noAccount: '还没有账号？',
    createNow: '立即注册',
    hasAccount: '已有账号？',
    signInNow: '立即登录',
    language: '简体中文',
  },
  en: {
    brand: 'RAG KNOWLEDGE BASE',
    headline: 'Turn knowledge into answers.',
    tagline: 'Your personal AI knowledge space',
    welcome: 'Welcome back',
    createAccount: 'Create your account',
    loginSubtitle: 'Sign in to your knowledge space',
    registerSubtitle: 'Start building your knowledge base',
    username: 'Username',
    usernamePlaceholder: 'Enter your username',
    password: 'Password',
    passwordPlaceholder: 'Enter your password',
    nickname: 'Display name',
    nicknamePlaceholder: 'Optional',
    login: 'Sign in',
    register: 'Create account',
    processing: 'Please wait…',
    noAccount: 'New here?',
    createNow: 'Create an account',
    hasAccount: 'Already have an account?',
    signInNow: 'Sign in',
    language: 'English',
  },
} as const

function validateAuthForm(authMode: AuthMode, form: AuthForm, language: Language) {
  const username = form.username.trim()
  const password = form.password
  const nickname = form.nickname.trim()
  const messages =
    language === 'zh-CN'
      ? {
          usernameLength: '用户名长度必须在 3 到 50 个字符之间',
          usernameFormat: '用户名只能包含字母、数字和下划线',
          passwordLength: '密码长度必须在 8 到 72 个字符之间',
          nicknameLength: '昵称不能超过 100 个字符',
        }
      : {
          usernameLength: 'Username must be 3 to 50 characters long',
          usernameFormat: 'Username can contain only letters, numbers, and underscores',
          passwordLength: 'Password must be 8 to 72 characters long',
          nicknameLength: 'Display name cannot exceed 100 characters',
        }

  if (username.length < 3 || username.length > 50) return messages.usernameLength
  if (!/^[a-zA-Z0-9_]+$/.test(username)) return messages.usernameFormat
  if (password.length < 8 || password.length > 72) return messages.passwordLength
  if (authMode === 'register' && nickname.length > 100) return messages.nicknameLength

  return ''
}

export function AuthPage() {
  const { signIn } = useAuth()
  const [authMode, setAuthMode] = useState<AuthMode>('login')
  const [authForm, setAuthForm] = useState<AuthForm>(emptyAuthForm)
  const [language, setLanguage] = useState<Language>('zh-CN')
  const [languageMenuOpen, setLanguageMenuOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const text = copy[language]

  function handleModeChange(nextMode: AuthMode) {
    if (nextMode === authMode) return

    setAuthMode(nextMode)
    setAuthForm(emptyAuthForm)
    setMessage('')
    setError('')
  }

  function handleLanguageChange(nextLanguage: Language) {
    setLanguage(nextLanguage)
    setLanguageMenuOpen(false)
  }

  async function runAction(action: () => Promise<void>) {
    setLoading(true)
    setError('')
    setMessage('')

    try {
      await action()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : language === 'zh-CN' ? '操作失败' : 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }

  async function handleAuthSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const validationError = validateAuthForm(authMode, authForm, language)
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
        setMessage(language === 'zh-CN' ? '注册成功，请登录' : 'Account created. Please sign in.')
        return
      }

      const login = await authApi.login({ username, password: authForm.password })
      signIn(login)
      setAuthForm(emptyAuthForm)
    })
  }

  return (
    <main className="app auth-shell">
      <header className="auth-topbar">
        <div className="auth-brand" aria-label={text.brand}>
          <span className="brand-mark">R</span>
          <span>{text.brand}</span>
        </div>

        <div className="language-menu">
          <button
            aria-expanded={languageMenuOpen}
            aria-haspopup="menu"
            className="language-trigger"
            onClick={() => setLanguageMenuOpen((current) => !current)}
            type="button"
          >
            {text.language}
            <span aria-hidden="true" className="language-chevron">⌄</span>
          </button>

          {languageMenuOpen && (
            <div className="language-options" role="menu">
              <button
                aria-checked={language === 'zh-CN'}
                className={language === 'zh-CN' ? 'selected' : ''}
                onClick={() => handleLanguageChange('zh-CN')}
                role="menuitemradio"
                type="button"
              >
                简体中文
                {language === 'zh-CN' && <span aria-hidden="true">✓</span>}
              </button>
              <button
                aria-checked={language === 'en'}
                className={language === 'en' ? 'selected' : ''}
                onClick={() => handleLanguageChange('en')}
                role="menuitemradio"
                type="button"
              >
                English
                {language === 'en' && <span aria-hidden="true">✓</span>}
              </button>
            </div>
          )}
        </div>
      </header>

      <section className="auth-layout" aria-label={authMode === 'login' ? text.welcome : text.createAccount}>
        <div className="auth-hero">
          <h1>{text.headline}</h1>
          <p>{text.tagline}</p>
        </div>

        <section className="auth-surface">
          <div className="auth-surface-header">
            <h2>{authMode === 'login' ? text.welcome : text.createAccount}</h2>
            <p>{authMode === 'login' ? text.loginSubtitle : text.registerSubtitle}</p>
          </div>

          <form className="auth-form" onSubmit={handleAuthSubmit}>
            <label>
              {text.username}
              <input
                autoComplete="username"
                maxLength={50}
                minLength={3}
                onChange={(event) => setAuthForm((current) => ({ ...current, username: event.target.value }))}
                pattern="[A-Za-z0-9_]+"
                placeholder={text.usernamePlaceholder}
                required
                value={authForm.username}
              />
            </label>

            <label>
              {text.password}
              <input
                autoComplete={authMode === 'login' ? 'current-password' : 'new-password'}
                maxLength={72}
                minLength={8}
                onChange={(event) => setAuthForm((current) => ({ ...current, password: event.target.value }))}
                placeholder={text.passwordPlaceholder}
                required
                type="password"
                value={authForm.password}
              />
            </label>

            {authMode === 'register' && (
              <label>
                {text.nickname}
                <input
                  maxLength={100}
                  onChange={(event) => setAuthForm((current) => ({ ...current, nickname: event.target.value }))}
                  placeholder={text.nicknamePlaceholder}
                  value={authForm.nickname}
                />
              </label>
            )}

            <button className="primary-button" disabled={loading} type="submit">
              {loading ? text.processing : authMode === 'login' ? text.login : text.register}
            </button>
          </form>

          <StatusMessage error={error} message={message} />

          <p className="auth-mode-switch">
            {authMode === 'login' ? text.noAccount : text.hasAccount}{' '}
            <button
              onClick={() => handleModeChange(authMode === 'login' ? 'register' : 'login')}
              type="button"
            >
              {authMode === 'login' ? text.createNow : text.signInNow}
            </button>
          </p>
        </section>
      </section>
    </main>
  )
}
