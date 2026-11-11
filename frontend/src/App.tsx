import { Navigate, Route, Routes } from 'react-router-dom'
import { AdminRoute, ProtectedRoute } from './components/ProtectedRoute'
import { AppLayout } from './layout/AppLayout'
import { AdminPage } from './pages/AdminPage'
import { AuthPage } from './pages/AuthPage'
import { ChatPage } from './pages/ChatPage'
import { KnowledgeBasePage } from './pages/KnowledgeBasePage'
import { useAuth } from './auth/useAuth'
import './App.css'

function App() {
  const { session } = useAuth()
  const homePath = session ? '/knowledge-bases' : '/login'

  return (
    <Routes>
      <Route
        path="/login"
        element={session ? <Navigate to="/knowledge-bases" replace /> : <AuthPage />}
      />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="/knowledge-bases" element={<KnowledgeBasePage />} />
          <Route path="/chat" element={<ChatPage />} />
          <Route
            path="/admin"
            element={
              <AdminRoute>
                <AdminPage />
              </AdminRoute>
            }
          />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to={homePath} replace />} />
    </Routes>
  )
}

export default App
