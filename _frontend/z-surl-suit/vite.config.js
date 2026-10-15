import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

/**
 * 应用层 = SPA，产物 dist/ 由 z-surl-admin 的 maven-resources-plugin 拷进
 * BOOT-INF/classes/static/（Mode 1 合体）。
 *
 * 【base 必须与 admin 的 context-path 一致】—— 这是 007 踩坑 #19：
 * base 与 server.servlet.context-path 不一致时，页面能打开但 /assets/* 全 404，
 * 而且开发时用 `vite dev`（base 几乎不起作用）根本复现不出来，只在 `java -jar` 后炸。
 * 本仓 z-surl-admin 的 context-path 固定为 `/`，所以这里固定 `'/'`。
 * 若将来给 admin 加了 context-path，**这两处必须同一次改动**。
 */
export default defineConfig({
  base: '/',
  resolve: {
    dedupe: ['react', 'react-dom', 'react-router-dom', 'antd', '@ant-design/icons', 'axios'],
    alias: { '@': path.resolve(__dirname, 'src'), '@fc': path.resolve(__dirname, '../z-surl-component/src/index.ts'), ...(process.env.LOCAL_SIBLINGS === '1' ? { '@yuku123/z-surl-component': '../z-surl-component/src' } : {}) }
  },
  plugins: [react()],
  server: {
    port: 5174,
    host: '0.0.0.0',
    proxy: {
      // dev 时前端跑在 5174、后端跑在 8080，用 proxy 抹掉跨域
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/s': { target: 'http://localhost:8080', changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    emptyOutDir: true,
    sourcemap: false,
    // 实测：antd 6 全量 import 的 vendor chunk 约 1.17MB（gzip ~378KB），这是
    // "React + antd 控制台"这个技术栈的体积下限，不是配置出错。阈值按实测值抬到
    // 1300：长期报警的构建会让人对警告脱敏，真出问题时也就跟着被忽略了。
    // 真要瘦身要走按需引入（unplugin-vue/React Components 式的 babel plugin），
    // 那是独立决策，不在本次技术栈对齐范围内。
    chunkSizeWarningLimit: 1300,
    // 不做 manualChunks 手工分派。原先按模块路径硬拆成 react/antd/vendor 三块，
    // 但 @yuku123/z-frontend-common 里的 AppLayout 依赖 antd、antd 又经
    // @ant-design/icons 回指它 —— 拆出来的 vendor → antd → vendor 是**循环 chunk**，
    // rollup 只能报一句 warning 就继续，产物在浏览器里直接炸：
    //   ReferenceError: Cannot access 'rn' before initialization（vendor chunk TDZ）
    // 症状极阴：构建绿、四个 chunk 全 200、控制台无日志，只是整页白屏。
    // 交给 rollup 自己分块就没有这个问题，且仍会把稳定的依赖单独切出来。
    rollupOptions: {}
  }
})
