import { isAxiosError } from 'axios'
import type { ApiResponse } from '../types/api'

export function getAuthError(error: unknown): string {
  if (!isAxiosError<ApiResponse<never>>(error)) {
    return error instanceof Error ? error.message : 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.'
  }
  if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
    return 'Máy chủ phản hồi quá lâu. Vui lòng thử lại.'
  }
  if (!error.response) return 'Không thể kết nối máy chủ. Vui lòng kiểm tra kết nối và thử lại.'

  const { status, data } = error.response
  switch (status) {
    case 400:
    case 409:
      return typeof data?.message === 'string' ? data.message : 'Vui lòng kiểm tra thông tin đã nhập.'
    case 401:
      return error.config?.url === '/auth/login'
        ? 'Email hoặc mật khẩu không đúng, hoặc tài khoản chưa hoạt động.'
        : 'Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.'
    case 403:
      return 'Bạn không có quyền thực hiện thao tác này.'
    default:
      return status >= 500
        ? 'Máy chủ đang gặp sự cố. Vui lòng thử lại sau.'
        : 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.'
  }
}
