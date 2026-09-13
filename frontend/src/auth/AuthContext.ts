import { createContext } from 'react'
import type { LoginRequest, RegisterRequest, UserResponse } from '../types/auth'

export interface AuthContextValue {
  user: UserResponse | null
  isAuthenticated: boolean
  isLoading: boolean
  error: string | null
  login: (payload: LoginRequest) => Promise<void>
  register: (payload: RegisterRequest) => Promise<void>
  logout: () => void
  retry: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
