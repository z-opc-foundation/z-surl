declare module '@yuku123/z-frontend-common' {
  import type { ReactElement, ReactNode } from 'react'

  export interface AppUser { name?: string; role?: string }
  export interface AppIcon { icon?: ReactNode; color?: string; label?: string }
  export interface AppLayoutMenuItem {
    key: string
    label?: ReactNode
    icon?: ReactNode
    disabled?: boolean
    children?: AppLayoutMenuItem[]
  }

  export function AppLayout(props: {
    menuItems?: AppLayoutMenuItem[]
    appTitle?: string
    appShort?: string
    appVersion?: string
    appIcon?: AppIcon
    appUser?: AppUser | null
    loginPath?: string
    headerExtra?: ReactNode
    loading?: boolean
  }): ReactElement

  export function ErrorBoundary(props: { children?: ReactNode }): ReactElement
}
