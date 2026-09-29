import {
  useCallback,
  useLayoutEffect,
  useMemo,
  useState,
} from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { configureHttpAuth } from '../api/http'
import type { LoginResponse } from '../types/api'
import { AuthContext } from './auth-context'
import {
  clearSession,
  loadSession,
  saveSession,
  type Session,
} from './session'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => loadSession())
  const navigate = useNavigate()

  const signOut = useCallback(() => {
    clearSession()
    setSession(null)
    navigate('/login', { replace: true })
  }, [navigate])

  const signIn = useCallback(
    (login: LoginResponse) => {
      setSession(saveSession(login))
      navigate('/knowledge-bases', { replace: true })
    },
    [navigate],
  )

  useLayoutEffect(() => {
    configureHttpAuth({
      getAccessToken: () => loadSession()?.token ?? null,
      onUnauthorized: signOut,
    })
  }, [signOut])

  const value = useMemo(
    () => ({
      session,
      signIn,
      signOut,
    }),
    [session, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
