import { useAuth } from '../auth/useAuth'

export function AccountPage() {
  const { user, logout } = useAuth()
  if (!user) return null
  return (
    <section className="auth-card">
      <h1>Tài khoản</h1>
      <dl className="account-details">
        <dt>Họ tên</dt><dd>{user.fullName}</dd>
        <dt>Email</dt><dd>{user.email}</dd>
        <dt>Vai trò</dt><dd>{user.role}</dd>
        {user.phone && <><dt>Số điện thoại</dt><dd>{user.phone}</dd></>}
      </dl>
      <button onClick={logout}>Đăng xuất</button>
    </section>
  )
}
