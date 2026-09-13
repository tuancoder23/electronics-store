import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { getAuthError } from '../auth/authError'
import { validateAuth } from '../auth/authValidation'

export function AuthForm({ mode }: { mode: 'login' | 'register' }) {
  const registering = mode === 'register'
  const { login, register } = useAuth()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [phone, setPhone] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const inFlight = useRef(false)
  const title = registering ? 'Đăng ký' : 'Đăng nhập'

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (inFlight.current) return
    const values = { fullName: fullName.trim(), email: email.trim(), password, phone: phone.trim() }
    const validationError = validateAuth(values, registering)
    setError(validationError)
    if (validationError) return
    inFlight.current = true
    setSubmitting(true)
    try {
      if (registering) {
        await register(values)
      } else {
        await login({ email: values.email, password })
      }
    } catch (cause: unknown) {
      setError(getAuthError(cause))
    } finally {
      setPassword('')
      setSubmitting(false)
      inFlight.current = false
    }
  }

  return (
    <section className="auth-card">
      <h1>{title}</h1>
      <p>{registering ? 'Tạo tài khoản Electronics Store.' : 'Chào mừng bạn quay lại Electronics Store.'}</p>
      <form className="auth-form" onSubmit={handleSubmit} noValidate aria-busy={submitting}>
        <fieldset disabled={submitting}>
          {registering && (
            <label htmlFor="fullName">Họ tên
              <input id="fullName" name="fullName" autoComplete="name" required maxLength={150}
                value={fullName} onChange={(event) => setFullName(event.target.value)} />
            </label>
          )}
          <label htmlFor="email">Email
            <input id="email" name="email" type="email" autoComplete="email" required
              maxLength={registering ? 255 : undefined} value={email}
              onChange={(event) => setEmail(event.target.value)} />
          </label>
          <label htmlFor="password">Mật khẩu
            <input id="password" name="password" type="password" required
              autoComplete={registering ? 'new-password' : 'current-password'}
              aria-describedby={registering ? 'password-help' : undefined}
              value={password} onChange={(event) => setPassword(event.target.value)} />
          </label>
          {registering && <p id="password-help" className="field-help">8–72 ký tự, tối đa 72 byte UTF-8.</p>}
          {registering && (
            <label htmlFor="phone">Số điện thoại (không bắt buộc)
              <input id="phone" name="phone" type="tel" autoComplete="tel" maxLength={30}
                value={phone} onChange={(event) => setPhone(event.target.value)} />
            </label>
          )}
          {error && <p className="form-error" role="alert">{error}</p>}
          <button type="submit" disabled={submitting}>{submitting ? 'Đang xử lý…' : title}</button>
        </fieldset>
      </form>
      <p className="auth-switch">
        {registering ? 'Đã có tài khoản? ' : 'Chưa có tài khoản? '}
        {submitting ? <span>Vui lòng chờ…</span> : (
          <Link to={registering ? '/login' : '/register'}>{registering ? 'Đăng nhập' : 'Đăng ký'}</Link>
        )}
      </p>
    </section>
  )
}
