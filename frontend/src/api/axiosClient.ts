import axios from 'axios'
import { clearToken, getToken } from '../auth/authToken'

const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
  timeout: 10_000,
})

axiosClient.interceptors.request.use((config) => {
  if (!config.baseURL?.trim()) {
    throw new Error('Thiếu cấu hình VITE_API_BASE_URL trong frontend/.env.')
  }

  // Public credential exchange must work even if a previous JWT has expired.
  const isAuthRequest = config.url === '/auth/login' || config.url === '/auth/register'
  const token = isAuthRequest ? null : getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  } else {
    config.headers.delete('Authorization')
  }
  return config
})

axiosClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const token = getToken()
      // A delayed 401 for an old session must not log out a newer session.
      if (token && error.config?.headers.get('Authorization') === `Bearer ${token}`) {
        clearToken()
      }
    }
    return Promise.reject(error)
  },
)

export default axiosClient
