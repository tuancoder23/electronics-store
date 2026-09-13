import axiosClient from './axiosClient'
import type { ApiResponse } from '../types/api'
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '../types/auth'

function unwrap<T>(response: ApiResponse<T>): T {
  if (response?.success !== true || !response.data) {
    throw new Error('Phản hồi xác thực từ máy chủ không hợp lệ.')
  }
  return response.data
}

function unwrapAuth(response: ApiResponse<AuthResponse>): AuthResponse {
  const data = unwrap(response)
  if (!data.accessToken?.trim() || data.tokenType !== 'Bearer' || !data.user) {
    throw new Error('Phản hồi xác thực từ máy chủ không hợp lệ.')
  }
  return data
}

export async function login(payload: LoginRequest, signal?: AbortSignal): Promise<AuthResponse> {
  const { data } = await axiosClient.post<ApiResponse<AuthResponse>>('/auth/login', payload, { signal })
  return unwrapAuth(data)
}

export async function register(payload: RegisterRequest, signal?: AbortSignal): Promise<AuthResponse> {
  const { data } = await axiosClient.post<ApiResponse<AuthResponse>>('/auth/register', payload, { signal })
  return unwrapAuth(data)
}

export async function getCurrentUser(signal?: AbortSignal): Promise<UserResponse> {
  const { data } = await axiosClient.get<ApiResponse<UserResponse>>('/users/me', { signal })
  const user = unwrap(data)
  if (!user.id || !user.email || !user.fullName ||
      !['USER', 'ADMIN'].includes(user.role) || user.status !== 'ACTIVE') {
    throw new Error('Thông tin tài khoản từ máy chủ không hợp lệ hoặc tài khoản chưa hoạt động.')
  }
  return user
}
