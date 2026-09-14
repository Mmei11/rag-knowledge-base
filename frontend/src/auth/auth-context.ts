import { createContext } from 'react'
import type { LoginResponse } from '../types/api'
import type { Session } from './session'

export type AuthContextValue = {
  session: Session | null
  signIn: (login: LoginResponse) => void
  signOut: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
