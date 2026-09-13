// @vitest-environment jsdom
import { StrictMode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AxiosError } from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import App from '../src/App'
import { AuthProvider } from '../src/auth/AuthProvider'
import { clearToken, getToken, setToken } from '../src/auth/authToken'
import { validateAuth } from '../src/auth/authValidation'
import { getAuthError } from '../src/auth/authError'
import axiosClient from '../src/api/axiosClient'
import * as authApi from '../src/api/authApi'

const profile = {
  id: 1, fullName: 'Auth Test', email: 'auth@example.test', phone: null,
  role: 'USER', status: 'ACTIVE', createdAt: '2026-09-13T12:00:00', updatedAt: '2026-09-13T12:00:00',
}
const token = 'test-session-token'
const password = ' TestPassword123! '
const defaultAdapter = axiosClient.defaults.adapter
const requests: InternalAxiosRequestConfig[] = []
let server: (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>

function respond(config: InternalAxiosRequestConfig, data: unknown, status = 200): AxiosResponse {
  return { config, data, status, statusText: String(status), headers: {} }
}
function success(config: InternalAxiosRequestConfig, data: unknown, status = 200) {
  return respond(config, { success: true, message: 'OK', data, timestamp: '2026-09-13T12:00:00' }, status)
}
function fail(config: InternalAxiosRequestConfig, status: number, message = 'Request failed'): never {
  throw new AxiosError(message, 'ERR_BAD_REQUEST', config, undefined,
    respond(config, { success: false, message, timestamp: '2026-09-13T12:00:00' }, status))
}
function mount(path = '/account') {
  return render(<StrictMode><MemoryRouter initialEntries={[path]}><AuthProvider><App /></AuthProvider></MemoryRouter></StrictMode>)
}
async function fillLogin() {
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Email'), profile.email)
  await user.type(screen.getByLabelText('Mật khẩu'), password)
  return user
}

beforeEach(() => {
  localStorage.clear()
  requests.length = 0
  server = async (config) => {
    if (config.url === '/auth/login' || config.url === '/auth/register') {
      return success(config, { accessToken: token, tokenType: 'Bearer', user: profile },
        config.url === '/auth/register' ? 201 : 200)
    }
    if (config.url === '/users/me') return success(config, profile)
    throw new Error('Unexpected test endpoint')
  }
  axiosClient.defaults.adapter = async (config) => {
    requests.push(config)
    return server(config)
  }
})
afterEach(() => {
  cleanup()
  axiosClient.defaults.adapter = defaultAdapter
  localStorage.clear()
  vi.restoreAllMocks()
})

describe('Authentication flow through React, context and Axios', () => {
  it('redirects a guest; logs in, stores only the token, sends Bearer, loads user and logs out', async () => {
    mount()
    const user = await fillLogin()
    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))
    await screen.findByText(profile.fullName)
    expect(getToken()).toBe(token)
    expect(Object.keys(localStorage)).toEqual(['authToken'])
    const login = requests.find((request) => request.url === '/auth/login')!
    expect(JSON.parse(login.data)).toEqual({ email: profile.email, password })
    expect(login.headers.has('Authorization')).toBe(false)
    expect(requests.find((request) => request.url === '/users/me')?.headers.get('Authorization')).toBe(`Bearer ${token}`)
    expect(screen.getByText('USER')).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }))
    await screen.findByRole('heading', { name: 'Đăng nhập' })
    expect(getToken()).toBeNull()
    expect(screen.queryByText(profile.email)).toBeNull()
  })

  it('registers with the exact fields and automatically verifies the returned token', async () => {
    mount('/register')
    const user = userEvent.setup()
    await user.type(await screen.findByLabelText('Họ tên'), profile.fullName)
    await user.type(screen.getByLabelText('Email'), profile.email)
    await user.type(screen.getByLabelText('Mật khẩu'), password)
    await user.type(screen.getByLabelText('Số điện thoại (không bắt buộc)'), '+84 12345678')
    await user.click(screen.getByRole('button', { name: 'Đăng ký' }))
    await screen.findByText(profile.fullName)
    const request = requests.find((item) => item.url === '/auth/register')!
    expect(JSON.parse(request.data)).toEqual({ fullName: profile.fullName, email: profile.email, password, phone: '+84 12345678' })
    expect(getToken()).toBe(token)
  })

  it('waits for backend verification on reload and uses its current role', async () => {
    setToken(token)
    let release: (() => void) | undefined
    server = (config) => new Promise((resolve) => {
      release = () => resolve(success(config, { ...profile, role: 'ADMIN' }))
    })
    mount('/login')
    expect(screen.getByRole('status').textContent).toContain('Đang xác thực')
    expect(screen.queryByText(profile.fullName)).toBeNull()
    await waitFor(() => expect(release).toBeTypeOf('function'))
    await act(async () => { release!() })
    await screen.findByText('ADMIN')
    expect(screen.queryByRole('heading', { name: 'Đăng nhập' })).toBeNull()
  })

  it('removes an invalid stored token and redirects to login', async () => {
    setToken('invalid-session')
    server = async (config) => fail(config, 401)
    mount()
    await screen.findByRole('heading', { name: 'Đăng nhập' })
    expect(getToken()).toBeNull()
    expect(requests.length).toBeLessThan(4)
  })

  it('keeps an unverified token on network failure, blocks account, then retries', async () => {
    setToken(token)
    server = async (config) => { throw new AxiosError('Network Error', 'ERR_NETWORK', config) }
    mount()
    expect((await screen.findByRole('alert')).textContent).toContain('Không thể kết nối')
    expect(screen.queryByText(profile.fullName)).toBeNull()
    expect(getToken()).toBe(token)
    server = async (config) => success(config, profile)
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await screen.findByText(profile.fullName)
  })

  it('disables submit while pending and displays bad credentials without retaining the password', async () => {
    let rejectRequest: (() => void) | undefined
    server = (config) => new Promise((_, reject) => {
      rejectRequest = () => { try { fail(config, 401) } catch (error) { reject(error) } }
    })
    mount('/login')
    const user = await fillLogin()
    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))
    expect((screen.getByRole('button', { name: 'Đang xử lý…' }) as HTMLButtonElement).disabled).toBe(true)
    await act(async () => { rejectRequest!() })
    expect((await screen.findByRole('alert')).textContent).toContain('Email hoặc mật khẩu không đúng')
    expect((screen.getByLabelText('Mật khẩu') as HTMLInputElement).value).toBe('')
    expect(getToken()).toBeNull()
  })

  it('does not restore an old current-user response after logout', async () => {
    setToken(token)
    let release: (() => void) | undefined
    server = (config) => new Promise((resolve) => { release = () => resolve(success(config, profile)) })
    mount()
    await waitFor(() => expect(release).toBeTypeOf('function'))
    await act(async () => { clearToken(); release!() })
    await screen.findByRole('heading', { name: 'Đăng nhập' })
    expect(screen.queryByText(profile.fullName)).toBeNull()
  })

  it('updates the mounted session when another tab logs out', async () => {
    setToken(token)
    mount()
    await screen.findByText(profile.fullName)
    await act(async () => {
      localStorage.removeItem('authToken')
      window.dispatchEvent(new StorageEvent('storage', { key: 'authToken', newValue: null, storageArea: localStorage }))
    })
    await screen.findByRole('heading', { name: 'Đăng nhập' })
  })

  it('reports storage failure without authenticating', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('Storage blocked') })
    mount('/login')
    const user = await fillLogin()
    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }))
    expect((await screen.findByRole('alert')).textContent).toContain('Không thể lưu phiên đăng nhập')
    expect(screen.queryByText(profile.fullName)).toBeNull()
    expect(getToken()).toBeNull()
  })
})

describe('JWT interceptor and validation boundaries', () => {
  it('does not send a stale JWT to login/register', async () => {
    setToken('old-session')
    await authApi.login({ email: profile.email, password })
    await authApi.register({ fullName: profile.fullName, email: profile.email, password })
    expect(requests.every((request) => !request.headers.has('Authorization'))).toBe(true)
  })

  it('does not let a delayed 401 clear a newer token', async () => {
    setToken('old-session')
    let rejectRequest: (() => void) | undefined
    server = (config) => new Promise((_, reject) => {
      rejectRequest = () => { try { fail(config, 401) } catch (error) { reject(error) } }
    })
    const result = authApi.getCurrentUser().catch(() => undefined)
    await waitFor(() => expect(rejectRequest).toBeTypeOf('function'))
    setToken('new-session')
    rejectRequest!()
    await result
    expect(getToken()).toBe('new-session')
  })

  it('preserves a session on 403 and masks server internals on 500', async () => {
    setToken(token)
    server = async (config) => fail(config, 403, 'private details')
    const forbidden = await authApi.getCurrentUser().catch((error: unknown) => error)
    expect(getAuthError(forbidden)).toContain('không có quyền')
    expect(getToken()).toBe(token)
    server = async (config) => fail(config, 500, 'private stack trace')
    const failure = await authApi.getCurrentUser().catch((error: unknown) => error)
    expect(getAuthError(failure)).toContain('Máy chủ đang gặp sự cố')
    expect(getAuthError(failure)).not.toContain('private')
  })

  it.each([400, 409])('shows the backend message for %s', async (status) => {
    server = async (config) => fail(config, status, 'Email already exists')
    const error = await authApi.register({ fullName: profile.fullName, email: profile.email, password }).catch((cause: unknown) => cause)
    expect(getAuthError(error)).toBe('Email already exists')
  })

  it('validates registration byte length and phone without trimming passwords', () => {
    const values = { fullName: profile.fullName, email: profile.email, password }
    expect(validateAuth(values, true)).toBeNull()
    expect(validateAuth({ ...values, password: 'é'.repeat(37) }, true)).toContain('72 byte')
    expect(validateAuth({ ...values, password: 'short' }, true)).toContain('8–72')
    expect(validateAuth({ ...values, phone: 'invalid' }, true)).toContain('Số điện thoại')
    expect(validateAuth({ ...values, password: 'short' }, false)).toBeNull()
  })
})
