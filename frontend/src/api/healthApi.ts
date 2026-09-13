import type { ApiResponse } from '../types/api'
import type { HealthResponse } from '../types/health'
import axiosClient from './axiosClient'

export async function getHealth(signal?: AbortSignal): Promise<HealthResponse> {
  const { data: response } = await axiosClient.get<ApiResponse<HealthResponse>>(
    '/health',
    { signal },
  )

  if (response.success !== true || response.data?.status !== 'UP') {
    throw new Error('Backend trả về kết quả health không hợp lệ hoặc chưa sẵn sàng.')
  }

  return response.data
}
