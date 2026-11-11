import { Navigate, Outlet } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from '../auth/useAuth'

export function ProtectedRoute() {
  const { session } = useAuth()

  if (!session) {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}

export function AdminRoute({ children }: { children: ReactNode }) {
  const { session } = useAuth()

  if (!session) {
    return <Navigate to="/login" replace />
  }

  if (session.user.role === 'USER') {
    return <Navigate to="/knowledge-bases" replace />
  }

  return children
}
