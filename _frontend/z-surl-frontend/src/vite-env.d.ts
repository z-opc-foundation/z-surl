/// <reference types="vite/client" />

/**
 * 自定义环境变量的类型声明。
 * 只在这里声明"本仓允许注入的变量" —— 这样拼错变量名会在 `tsc --noEmit` 阶段就报错，
 * 而不是运行期悄悄拿到 undefined（`VITE_SURL_API_BAE` 拼错时症状是"请求发到当前域名
 * 相对路径然后 404"，排查起来很绕）。
 */
interface ImportMetaEnv {
  /** API 基础路径。留空 = 同源相对路径（Mode 1/2 默认形态）。 */
  readonly VITE_SURL_API_BASE?: string
  /** 对外短链前缀。留空 = 取 window.location.origin。 */
  readonly VITE_SURL_PUBLIC_ORIGIN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
