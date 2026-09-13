import { useAuth } from '../auth/useAuth'

export function SessionStatus() {
  const { isLoading, error, retry, logout } = useAuth()
  return (
    <section className="auth-card">
      {isLoading ? <p role="status">Đang xác thực phiên đăng nhập…</p> : (
        <>
          <h1>Chưa thể xác thực phiên</h1>
          <p role="alert">{error}</p>
          <div className="auth-actions">
            <button onClick={retry}>Thử lại</button>
            <button className="secondary" onClick={logout}>Về đăng nhập</button>
          </div>
        </>
      )}
    </section>
  )
}
