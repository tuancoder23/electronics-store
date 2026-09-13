import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { SessionStatus } from './SessionStatus'

export function ProtectedRoute() {
  const { isAuthenticated, isLoading, error } = useAuth()
  if (isLoading || error) return <SessionStatus />
  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />
}
