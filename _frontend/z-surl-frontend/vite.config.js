import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'

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
    alias: {
      '@': resolve(__dirname, 'src'),
      // 组件层走源码别名：改组件立刻热更新，不必先 build 组件库
      '@fc': resolve(__dirname, '../z-surl-frontend-component/src')
    }
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
    // antd 6 全量 import 时单 chunk 会到 1.4MB+（gzip ~470KB），
    // vite 会报 "chunks are larger than 500 kB"。按 vendor 拆开后：
    //   - 业务代码迭代时浏览器只重新下 app 那一小段
    //   - antd/rc-* 变成长期缓存的稳定 chunk
    //
    // 用**函数形式按模块路径**分派，而不是 `manualChunks: { antd: ['antd'] }` 的
    // 入口数组形式：后者在 antd 这种"自身也 import react"的依赖图下会把 react
    // 留在主 chunk、同时生成一个 1 字节的空 react chunk —— 总量没降还多了个废产物
    // （实测踩过：拆完 index 765KB + antd 683KB + react 0.00KB，比不拆还难看）。
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (/node_modules\/(react|react-dom|scheduler)\//.test(id)) return 'react'
          if (/node_modules\/(antd|@ant-design|rc-[^/]+|@rc-component)\//.test(id)) return 'antd'
          return 'vendor'
        }
      }
    }
  }
})
