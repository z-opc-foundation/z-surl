/**
 * z-surl 组件层的**数据契约**.
 *
 * 这些类型刻意与后端 DO / VO 的字段名一一对应（camelCase），
 * 消费方（z-opc 的 SurlManagement、z-surl-admin 控制台）拿到的就是后端 JSON 原样，
 * 中间不需要一层转换 —— 转换层是字段漂移最常见的产地。
 *
 * 字段可空性按后端实际写：`@JsonInclude(NON_NULL)`（见 z-surl-admin application.yml）
 * 意味着后端不返回 null 字段，所以这里全部是可选的，消费方要自己兜默认值。
 */

/** 一条短链记录（对应后端 SurlMappingVO） */
export interface SurlItem {
  id?: number | null
  /** 短码，Base62 8 字符 */
  mapKey: string
  /** 原长链接 */
  originUrl: string
  /** 归属租户；空 = 公共链接 */
  tenantCode?: string | null
  /** 过期时间（ISO 字符串）；空 = 永不过期 */
  expireAt?: string | null
  /** 命中次数 */
  hits?: number | null
  createdAt?: string | null
  createdBy?: string | null
  remark?: string | null
}

/** 生成短链的入参（对应后端 SurlShortenRequest） */
export interface SurlShortenInput {
  originUrl: string
  tenantCode?: string
  /** 有效期毫秒；0 或不传 = 永不过期 */
  ttlMillis?: number
  remark?: string
}

/** 生成短链的返回（对应后端 SurlShortenResponse） */
export interface SurlShortenResult {
  mapKey: string
  originUrl: string
  expireAt?: string | null
  hits?: number | null
}

/** 有效期的预设档位。
 *
 * 用预设而不是让用户填毫秒数：毫秒是实现细节，暴露给用户既容易填错
 * （把"1 天"填成 1000 就成了 1 秒），也让组件层绑死后端的 ttl 语义。
 * 0 必须表示"永不过期"，与后端 SurlService#computeExpireAt 的约定一致。
 */
export const SURL_TTL_PRESETS = [
  { label: '永不过期', value: 0 },
  { label: '1 小时', value: 60 * 60 * 1000 },
  { label: '1 天', value: 24 * 60 * 60 * 1000 },
  { label: '7 天', value: 7 * 24 * 60 * 60 * 1000 },
  { label: '30 天', value: 30 * 24 * 60 * 60 * 1000 }
] as const

export type SurlTtlPreset = (typeof SURL_TTL_PRESETS)[number]
