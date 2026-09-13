import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import * as authApi from '../api/authApi'
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '../types/auth'
import { AuthContext } from './AuthContext'
import { clearToken, getToken, setToken, subscribeTokenChange } from './authToken'
import { getAuthError } from './authError'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null)
  const [isLoading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const revision = useRef(0)
  const pending = useRef<AbortController | null>(null)

  const cancelPending = useCallback(() => {
    ++revision.current
    pending.current?.abort()
  }, [])

  const restoreSession = useCallback(async () => {
    const current = ++revision.current
    pending.current?.abort()
    const controller = new AbortController()
    pending.current = controller
    setUser(null)
    setError(null)
    if (!getToken()) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const profile = await authApi.getCurrentUser(controller.signal)
      if (current === revision.current) setUser(profile)
    } catch (cause: unknown) {
      if (current === revision.current && !controller.signal.aborted) {
        setError(getAuthError(cause))
      }
    } finally {
      if (current === revision.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    const unsubscribe = subscribeTokenChange(() => { void restoreSession() })
    void restoreSession()
    return () => {
      unsubscribe()
      cancelPending()
    }
  }, [restoreSession, cancelPending])

  async function authenticate(request: (signal: AbortSignal) => Promise<AuthResponse>) {
    const current = ++revision.current
    pending.current?.abort()
    const controller = new AbortController()
    pending.current = controller
    const response = await request(controller.signal)
    if (current !== revision.current || controller.signal.aborted) return
    // Token changes trigger /users/me; authentication is confirmed by the backend.
    setToken(response.accessToken)
  }

  const login = (payload: LoginRequest) => authenticate((signal) => authApi.login(payload, signal))
  const register = (payload: RegisterRequest) => authenticate((signal) => authApi.register(payload, signal))
  const logout = () => clearToken()

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: user !== null, isLoading, error,
      login, register, logout, retry: () => { void restoreSession() } }}>
      {children}
    </AuthContext.Provider>
  )
}
