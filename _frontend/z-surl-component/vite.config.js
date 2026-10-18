import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'

/**
 * 组件层 = library mode（005 规范 §1.2）：输出 es + d.ts，不产 index.html、不接路由。
 *
 * 产物形态刻意保持"纯 JS + d.ts"而不是 UMD/IIFE：消费方全是 ESM 工具链（Vite/webpack5），
 * UMD 在这里只会增加体积和 tree-shaking 的噪音。
 */
export default defineConfig({
  build: {
    lib: {
      // 两个 entry 对应 exports 两张脸: './dist/index.js'(.) 与 './dist/pages.js'(./pages,
      // lead 005 §8.2/§8.6 #3)。只声明 index 时产物里没有 pages.js, 而 exports 还指着它。
      entry: {
        index: resolve(__dirname, 'src/index.ts'),
        pages: resolve(__dirname, 'src/pages.ts'),
      },
      name: 'ZSurlFrontendComponent',
      formats: ['es'],
    },
    // 业务方（z-opc）与 admin 都要 tree-shake，关掉这两项才有效
    rollupOptions: {
      external: ['react', 'react-dom', 'react/jsx-runtime', 'antd'],
      output: {
        globals: {
          react: 'React',
          'react-dom': 'ReactDOM',
          'antd': 'antd'
        }
      }
    },
    sourcemap: true,
    emptyOutDir: true
  },
  plugins: [react()],
  // 组件层是库，不产 HTML，所以没有 base 概念
  define: {
    // antd 6 在 React 19 下需要这个标记才能走 CSS-in-JS 的正确路径
    'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV || 'production')
  }
})
