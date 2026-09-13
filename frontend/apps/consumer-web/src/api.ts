const base = import.meta.env.VITE_API_BASE_URL ?? ''
export class ApiError extends Error { constructor(message: string, public status: number) { super(message) } }
// Read integer ID tokens before JSON.parse, including legacy services that serialize Java longs as numbers.
export function parseJson(text: string): unknown {
  const lossless = text.replace(/"(?:[^"\\]|\\.)*"|(-?\d{16,})(?=\s*[,}\]])/g, (token, integer) => integer ? `"${integer}"` : token)
  return JSON.parse(lossless, (key, value) => /(^id$|Id$)/.test(key) && typeof value === 'number' ? String(value) : value)
}
export async function request<T>(path: string, token = '', body?: unknown): Promise<T> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 20000)
  try {
    const response = await fetch(`${base}/api/consumer/v1${path}`, { method: body === undefined ? 'GET' : 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body === undefined ? undefined : JSON.stringify(body), signal: controller.signal })
    const text = await response.text()
    let data: any
    try { data = text ? parseJson(text) : undefined } catch { data = undefined }
    if (!response.ok) throw new ApiError(data?.message ?? data?.detail ?? ({ 401: '登录已过期或账号密码错误，请重新登录', 403: '当前操作不可用，请确认品牌和账号状态', 404: '内容不存在或已失效', 409: '商品或订单状态已变化，请刷新后重试', 429: '操作过于频繁，请稍后再试' }[response.status] ?? '服务暂不可用，请稍后重试'), response.status)
    return data?.data ?? data
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError('网络暂不可用，操作结果可能尚未确认，请重试或刷新订单', 0)
  } finally { clearTimeout(timeout) }
}
