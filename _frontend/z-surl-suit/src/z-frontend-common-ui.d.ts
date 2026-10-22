/**
 * 基座 UI 桶（@yuku123/z-frontend-common/ui）的类型声明。
 *
 * 【为什么手写而不是等基座出 d.ts】
 * 基座是纯 JS 包（dist 里只有 js，没有 .d.ts），exports 的 './ui' 子路径也没配 types，
 * TS 直接 import 会报 TS7016 "Could not find a declaration file"。本仓是 TS 仓，
 * 所以只能像 suit 侧已有的 z-frontend-common.d.ts 那样手写一份最小声明。
 *
 * 【维护约定】
 * 这里只声明本仓真正用到的组件；**props 以基座源码 src/components/ui/*.jsx 为准**，
 * 基座升版改了签名要回来同步（尤其是 ErrorState 的 variant / EmptyState 的 size）。
 */
declare module '@yuku123/z-frontend-common/ui' {
  import type { CSSProperties, ReactElement, ReactNode } from 'react'

  export function EmptyState(props: {
    icon?: ReactNode
    title?: string
    description?: ReactNode
    actionText?: string
    onAction?: () => void
    size?: 'sm' | 'md' | 'lg'
    style?: CSSProperties
  }): ReactElement

  export function ErrorState(props: {
    error?: unknown
    title?: string
    description?: ReactNode
    onRetry?: () => void
    retryText?: string
    variant?: 'inline' | 'full'
    style?: CSSProperties
  }): ReactElement

  export function RefreshButton(props: {
    onClick?: () => void
    loading?: boolean
    title?: string
  }): ReactElement
}
