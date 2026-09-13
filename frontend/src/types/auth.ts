export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest extends LoginRequest {
  fullName: string
  phone?: string
}

export interface UserResponse {
  id: number
  fullName: string
  email: string
  phone: string | null
  role: 'USER' | 'ADMIN'
  status: 'ACTIVE' | 'INACTIVE'
  createdAt: string
  updatedAt: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  user: UserResponse
}
