const TOKEN_KEY = 'authToken'
const CHANGE_EVENT = 'auth-token-changed'
let storageReadable = true

export function getToken(): string | null {
  if (!storageReadable) return null
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token)
    storageReadable = true
  } catch {
    throw new Error('Không thể lưu phiên đăng nhập. Hãy cho phép lưu trữ trong trình duyệt.')
  }
  window.dispatchEvent(new Event(CHANGE_EVENT))
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    // If browser storage becomes unavailable, discard this tab's session too.
    storageReadable = false
  } finally {
    window.dispatchEvent(new Event(CHANGE_EVENT))
  }
}

export function subscribeTokenChange(listener: () => void): () => void {
  const onStorage = (event: StorageEvent) => {
    if (event.storageArea === localStorage && (event.key === TOKEN_KEY || event.key === null)) {
      storageReadable = true
      listener()
    }
  }
  window.addEventListener(CHANGE_EVENT, listener)
  window.addEventListener('storage', onStorage)
  return () => {
    window.removeEventListener(CHANGE_EVENT, listener)
    window.removeEventListener('storage', onStorage)
  }
}
