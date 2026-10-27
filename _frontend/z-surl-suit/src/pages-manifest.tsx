import type { ComponentType, ReactNode } from 'react'
import { HomeOutlined, LinkOutlined, ReadOutlined } from '@ant-design/icons'
import HomePage from './HomePage'
import ConsolePage from './ConsolePage'
import ComponentDocPage from './pages/ComponentDoc'

export interface MenuItem { key: string; label: string; icon: ReactNode; desc?: string }
export interface RouteEntry { path: string; Component: ComponentType }

/** 菜单 + 路由清单（lead 008 §10/§14/§16）。 */
export const menuItems: MenuItem[] = [
    { key: '/z-surl/home', label: '首页', icon: <HomeOutlined /> },
    { key: '/z-surl/console', label: '短链控制台', icon: <LinkOutlined /> },
    { key: '/z-surl/docs', label: '组件文档', icon: <ReadOutlined /> },
]

export const routes: RouteEntry[] = [
    { path: '/z-surl/home', Component: HomePage },
    { path: '/z-surl/console', Component: ConsolePage },
    { path: '/z-surl/docs', Component: ComponentDocPage },
]

// 主壳接线用：文档页统一以 ComponentDocPage 这个名字从主入口导出。
export { default as ComponentDocPage } from './pages/ComponentDoc'
