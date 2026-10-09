import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App as AntdApp, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import App from './App'

/**
 * 应用入口.
 *
 * ConfigProvider + AntdApp 包一层不是为了好看：antd 6 的 `message` / `notification` /
 * `Modal.confirm` 走 context 拿主题与 locale，用静态 `message.success(...)` 会在
 * ConfigProvider 之外时丢主题、丢中文 locale。这里统一用 `App.useApp()` 取。
 */
const container = document.getElementById('root')
if (!container) {
  throw new Error('#root 不存在：index.html 与本入口不同步')
}

createRoot(container).render(
  <StrictMode>
    <ConfigProvider locale={zhCN} theme={{ token: { colorPrimary: '#1677ff' } }}>
      <AntdApp>
        <App />
      </AntdApp>
    </ConfigProvider>
  </StrictMode>
)
