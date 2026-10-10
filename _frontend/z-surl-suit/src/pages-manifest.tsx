import type { ComponentType, ReactNode } from 'react'
import { HomeOutlined, LinkOutlined } from '@ant-design/icons'
import HomePage from './HomePage'
import ConsolePage from './ConsolePage'

export interface MenuItem { key: string; label: string; icon: ReactNode }
export interface RouteEntry { path: string; Component: ComponentType }

/** 菜单 + 路由清单（lead 008 §10/§14/§16）。 */
export const menuItems: MenuItem[] = [
    { key: '/z-surl/home', label: '首页', icon: <HomeOutlined /> },
    { key: '/z-surl/console', label: '短链控制台', icon: <LinkOutlined /> },
]

export const routes: RouteEntry[] = [
    { path: '/z-surl/home', Component: HomePage },
    { path: '/z-surl/console', Component: ConsolePage },
]
