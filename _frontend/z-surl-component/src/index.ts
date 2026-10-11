/**
 * z-surl 组件层出口.
 *
 * 命名分层说明（006 文档 z-render 的教训）：
 *   这里**只有**短链领域的符号。通用渲染引擎/字段类型注册表在 `@yuku123/z-frontend-render`，
 *   两边都有 "SurlItem"/"FieldType" 这类同名词时不要往这里塞 barrel 混装 ——
 *   合并 barrel 会让同名不同义的类型互相覆盖。
 */
export { default as SurlListView } from './SurlListView'
export type { SurlListViewProps } from './SurlListView'

export { default as SurlShortenForm } from './SurlShortenForm'
export type { SurlShortenFormProps } from './SurlShortenForm'

export { default as SurlStatsRow } from './SurlStatsRow'
export type { SurlStatsRowProps } from './SurlStatsRow'

export { SURL_TTL_PRESETS } from './types'
export type { SurlItem, SurlShortenInput, SurlShortenResult, SurlTtlPreset } from './types'
