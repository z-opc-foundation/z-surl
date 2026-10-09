/**
 * z-surl 后端 API 适配层（应用层专用）。
 *
 * 【与组件层的边界】组件层只收 props 不取数（005 规范 §5），所以"怎么调后端"这件事
 * 完全落在本文件。上层 App 只认这里的函数签名。
 *
 * 【为什么不用 axios】组织前端统一 fetch wrapper（001_技术栈全景：HTTP 层用
 * src/utils/request.ts 风格的薄封装）。三个端点不值得引一个 HTTP 库。
 */

import type { SurlItem, SurlShortenInput, SurlShortenResult } from '@fc'

/** 后端 com.zifang.util.core.meta.Result 的 JSON 形状（已核对 z-util-core 1.0.8 源码）：
 *  { data, success, code, message } */
interface Result<T> {
  data: T
  success: boolean
  code: number
  message: string | null
}

export class SurlApiError extends Error {
  readonly code: number
  constructor(message: string, code: number) {
    super(message)
    this.name = 'SurlApiError'
    this.code = code
  }
}

/** API 基础路径。
 *
 * 走相对路径（而不是拼 origin）有两个原因：
 *   1. Mode 1 合体时前端由同一个 Spring Boot 进程伺服，同源，不需要 CORS；
 *   2. Mode 2/3 分体时 nginx 把 /api 反代到后端，浏览器看到的仍是同源 /api。
 * 只有"前端独立域名 + 直连后端"才需要设 VITE_SURL_API_BASE。
 */
const API_BASE = (import.meta.env?.VITE_SURL_API_BASE as string | undefined)?.replace(/\/$/, '') ?? ''

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {})
    }
  })

  // 跳转面 404 返回的是 HTTP 404 + Result JSON，别的错误可能是 500 + HTML
  const text = await res.text()
  let body: Result<T> | null = null
  try {
    body = text ? (JSON.parse(text) as Result<T>) : null
  } catch {
    body = null
  }

  if (!res.ok || !body || body.success !== true) {
    throw new SurlApiError(body?.message ?? `HTTP ${res.status}`, body?.code ?? res.status)
  }
  return body.data
}

/**
 * POST /api/ctc/surl/shorten —— 生成短码。
 *
 * 路径里的 `ctc` 是历史遗留（仓名 z-surl，从 z-ctc 抽出时沿用）。
 * **不要改**：z-opc 的 SurlManagement 控制台和 250 的部署健康检查都按这组路径发请求，
 * 改路径会让控制台 404、健康检查判死。详见 SurlController 的类注释。
 */
export function shorten(input: SurlShortenInput): Promise<SurlShortenResult> {
  return request<SurlShortenResult>('/api/ctc/surl/shorten', {
    method: 'POST',
    body: JSON.stringify({
      originUrl: input.originUrl,
      tenantCode: input.tenantCode,
      ttlMillis: input.ttlMillis,
      remark: input.remark
    })
  })
}

/** GET /api/ctc/surl/info?mapKey=xxx —— 查询元信息。不存在抛 404。 */
export async function info(mapKey: string): Promise<SurlItem> {
  const data = await request<SurlItem>(
    `/api/ctc/surl/info?mapKey=${encodeURIComponent(mapKey)}`
  )
  return data
}

/** DELETE /api/ctc/surl?mapKey=xxx —— 失效。不存在抛 404。 */
export function invalidate(mapKey: string): Promise<unknown> {
  return request<unknown>(
    `/api/ctc/surl?mapKey=${encodeURIComponent(mapKey)}`,
    { method: 'DELETE' }
  )
}

/** 短码 → 完整短链 URL。跳转面是 /s/{mapKey}（不是 /api/... ），要短。 */
export function buildShortUrl(mapKey: string): string {
  const origin =
    (import.meta.env?.VITE_SURL_PUBLIC_ORIGIN as string | undefined)?.replace(/\/$/, '') ??
    (typeof window !== 'undefined' ? window.location.origin : '')
  return `${origin}/s/${encodeURIComponent(mapKey)}`
}
