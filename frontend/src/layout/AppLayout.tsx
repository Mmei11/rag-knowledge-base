import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export function AppLayout() {
  const { session, signOut } = useAuth()

  return (
    <main className="app workspace">
      <header className="topbar">
        <div className="brand-block compact">
          <span className="brand-mark">R</span>
          <div>
            <h1>智能知识库</h1>
            <p>{session?.user.nickname || session?.user.username}</p>
          </div>
        </div>

        <nav className="topnav" aria-label="主导航">
          <NavLink to="/knowledge-bases">知识库</NavLink>
          <NavLink to="/chat">智能问答</NavLink>
        </nav>

        <button className="ghost-button" type="button" onClick={signOut}>
          退出登录
        </button>
      </header>

      <Outlet />
    </main>
  )
}
