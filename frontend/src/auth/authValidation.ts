import type { RegisterRequest } from '../types/auth'

export function validateAuth(values: RegisterRequest, registering: boolean): string | null {
  if (registering && (!values.fullName.trim() || values.fullName.trim().length > 150)) {
    return 'Họ tên bắt buộc và không được vượt quá 150 ký tự.'
  }
  const email = values.email.trim()
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || (registering && email.length > 255)) {
    return 'Vui lòng nhập email hợp lệ.'
  }
  if (!values.password.trim()) return 'Vui lòng nhập mật khẩu.'
  if (registering && (values.password.length < 8 || values.password.length > 72 ||
      new TextEncoder().encode(values.password).length > 72)) {
    return 'Mật khẩu cần 8–72 ký tự và tối đa 72 byte UTF-8.'
  }
  if (registering && values.phone && !/^[0-9+() .-]{7,30}$/.test(values.phone)) {
    return 'Số điện thoại cần 7–30 ký tự: chữ số, +, (), dấu cách, dấu chấm hoặc gạch ngang.'
  }
  return null
}
