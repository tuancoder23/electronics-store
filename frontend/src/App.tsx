import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './components/ProtectedRoute'
import { GuestRoute } from './components/GuestRoute'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { AccountPage } from './pages/AccountPage'
import HealthPage from './pages/HealthPage'
import './App.css'

function App() {
  return (
    <>
      <header className="app-header">
        <Link className="brand" to="/account">Electronics Store</Link>
        <nav aria-label="Điều hướng chính">
          <Link to="/account">Tài khoản</Link>
          <Link to="/health">Kết nối backend</Link>
        </nav>
      </header>
      <main className="app-content">
        <Routes>
          <Route path="/" element={<Navigate to="/account" replace />} />
          <Route path="/health" element={<HealthPage />} />
          <Route element={<GuestRoute />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Route>
          <Route element={<ProtectedRoute />}>
            <Route path="/account" element={<AccountPage />} />
          </Route>
          <Route path="*" element={<section className="auth-card"><h1>Không tìm thấy trang</h1><Link to="/">Về trang chủ</Link></section>} />
        </Routes>
      </main>
    </>
  )
}

export default App
