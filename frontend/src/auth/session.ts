import type { LoginResponse, UserResponse } from '../types/api'

export type Session = {
  token: string
  user: UserResponse
}

const SESSION_KEY = 'ragkb.session'

export function loadSession(): Session | null {
  const value = localStorage.getItem(SESSION_KEY)
  if (!value) {
    return null
  }

  try {
    return JSON.parse(value) as Session
  } catch {
    localStorage.removeItem(SESSION_KEY)
    return null
  }
}

export function saveSession(login: LoginResponse): Session {
  const session = {
    token: login.token,
    user: login.user,
  }
  localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  return session
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY)
}
