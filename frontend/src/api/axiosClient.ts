import axios from 'axios'

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

  // TODO(auth): Read the JWT from the future auth module and, when present,
  // attach it with config.headers.set('Authorization', `Bearer ${token}`).
  return config
})

export default axiosClient
