import { Component, useEffect, useState } from 'react'
import type { ErrorInfo, ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from '@yuku123/z-frontend-common'
import { menuItems, routes } from './pages-manifest'
import LoginPage from './LoginPage'
import { Button, Result } from 'antd'

interface EBProps { children?: ReactNode }
interface EBState { err: Error | null }

class ErrorBoundary extends Component<EBProps, EBState> {
    constructor(props: EBProps) { super(props); this.state = { err: null } }
    static getDerivedStateFromError(err: Error): EBState { return { err } }
    componentDidCatch(err: Error, info: ErrorInfo) { console.error('[App ErrorBoundary]', err, info) }
    render() {
        if (this.state.err) return <pre style={{ padding: 24, color: 'red', whiteSpace: 'pre-wrap' }}>{String(this.state.err?.stack || this.state.err)}</pre>
        return this.props.children
    }
}

function NotFound() {
    return (
        <Result
            status="404"
            title="页面不存在"
            subTitle="路由表里没有这条路径。"
            extra={<Button type="primary" onClick={() => { window.location.href = '/z-surl/home' }}>去首页</Button>}
        />
    )
}

function LoginRoute() {
    return <LoginPage />
}

function ProtectedShell() {
    const [user, setUser] = useState<{ name?: string; role?: string } | null>(null)
    const [ready, setReady] = useState(false)

    useEffect(() => {
        if (!localStorage.getItem('token')) {
            window.location.replace('/z-surl/login')
            return
        }
        const raw = localStorage.getItem('userInfo')
        if (raw) { try { setUser(JSON.parse(raw)) } catch { setUser({ name: raw }) } }
        setReady(true)
    }, [])

    if (!ready) return null

    return (
        <AppLayout
            menuItems={menuItems}
            appTitle="z-surl 控制台"
            appShort="z-surl"
            appVersion="1.0.0"
            appIcon={{
                icon: <img src="/icon.png" alt="z-surl 控制台" style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: 8 }} />,
                color: '#ef4444',
                label: 'z-surl 控制台',
            }}
            appUser={user}
        />
    )
}

/** lead 008 §16 suit 一次整合：登录路由 + 鉴权壳 + URL 即状态（§11/§14）。 */
export default function App() {
    return (
        <ErrorBoundary>
            <BrowserRouter>
                <Routes>
                    <Route path="/z-surl/login" element={<LoginRoute />} />
                    <Route element={<ProtectedShell />}>
                        <Route path="/" element={<Navigate to={menuItems[0].key} replace />} />
                        {routes.map((r) => (
                            <Route key={r.path} path={r.path} element={<r.Component />} />
                        ))}
                        <Route path="*" element={<NotFound />} />
                    </Route>
                </Routes>
            </BrowserRouter>
        </ErrorBoundary>
    )
}
