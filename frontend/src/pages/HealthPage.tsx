import { useEffect, useState } from 'react'
import { isAxiosError } from 'axios'
import { getHealth } from '../api/healthApi'
import type { HealthResponse } from '../types/health'


type HealthState =
  | { status: 'loading' }
  | { status: 'online'; data: HealthResponse }
  | { status: 'error'; message: string }

function HealthPage() {
  const [health, setHealth] = useState<HealthState>({ status: 'loading' })

  useEffect(() => {
    const controller = new AbortController()

    async function checkHealth() {
      try {
        const data = await getHealth(controller.signal)
        if (!controller.signal.aborted) {
          setHealth({ status: 'online', data })
        }
      } catch (error: unknown) {
        if (controller.signal.aborted) return

        let message = 'Không thể kiểm tra kết nối backend.'
        if (isAxiosError(error)) {
          if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
            message = 'Backend không phản hồi trong thời gian chờ 10 giây.'
          } else if (error.response) {
            message = `Health API trả về lỗi HTTP ${error.response.status}.`
          } else {
            message = 'Không thể kết nối backend. Hãy kiểm tra server, mạng và cấu hình CORS.'
          }
        } else if (error instanceof Error) {
          message = error.message
        }

        setHealth({ status: 'error', message })
      }
    }

    void checkHealth()
    return () => controller.abort()
  }, [])

  return (
    <section className="health-check">
      <h1>Electronics Store</h1>
      <p>Kiểm tra kết nối backend</p>
      <div className="health-result" role="status" aria-live="polite">
        {health.status === 'loading' && <p>Đang kiểm tra kết nối backend…</p>}
        {health.status === 'online' && (
          <>
            <h2>Backend online</h2>
            <p>{health.data.message}</p>
            <p>Trạng thái: {health.data.status} · Phiên bản: {health.data.version}</p>
          </>
        )}
        {health.status === 'error' && (
          <>
            <h2>Lỗi kết nối backend</h2>
            <p>{health.message}</p>
          </>
        )}
      </div>
    </section>
  )
}

export default HealthPage
