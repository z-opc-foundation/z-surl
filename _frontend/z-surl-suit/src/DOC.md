# z-surl-suit

z-surl（短网址服务）的**独立运行壳**（lead 005 §9.1 suit，lead 008 §16 一次整合）：把「短码生成 / 按短码查询 / 命中统计 /
失效 / 登录」这一整套短链管理页面收敛成一个 SPA，由 `z-surl-admin` 在同一进程内伺服静态产物（Mode 1 合体）。

- **npm 包名**：`@yuku123/z-surl-suit`（`private: true`，**不发布 npm**，只服务本仓 admin）
- **宿主接入**：无（独立壳）。仓内对外出口是 `src/index.ts`（页面清单 + 文档页）
- **组件层**：`@yuku123/z-surl-component`（本壳通过 vite alias `@fc` 直接引它的源码），
  提供 `SurlListView` / `SurlShortenForm` / `SurlStatsRow` 与 `SurlItem` 等类型
- **后端**：`/api/ctc/surl/**`（管理面）+ `/s/{短码}`（跳转面），同源相对路径
- **技术栈**：React 19 + antd 6 + react-router-dom 7 + TypeScript 5.6 + vite（**应用层**，基座内联进 bundle）

> ⚠ 页面代码住在本壳 `src/` 下，`z-surl-component` 目前只有**可复用视图组件**与一份骨架 manifest
> （见 `z-surl-component/src/pages-manifest.tsx` 的占位说明）。
> 因此本文档描述的是**本壳自身的能力**。

---

## 一、能力概览

路由前缀 `/z-surl`，菜单与路由都在 `src/pages-manifest.tsx` 里声明，`src/App.tsx` 读数组自己组装。

| 页面 | 路由 | 源文件 | 能力 |
|------|------|--------|------|
| 首页 | `/z-surl/home` | `src/HomePage.tsx` | 欢迎语 + 按 `menuItems` 渲染的快捷入口卡片 |
| 短链控制台 | `/z-surl/console` | `src/ConsolePage.tsx` | 生成短链 / 按短码查询 / 失效 / 本次会话短链列表 / 统计行 |
| 登录 | `/z-surl/login` | `src/LoginPage.tsx` | mock 登录（**直接在 `App.tsx` 里注册路由，不在 `routes` 数组**） |
| 组件文档 | `/z-surl/docs` | `src/pages/ComponentDoc.tsx` | 本文件渲染页（左侧 240px TOC + 右侧正文） |

## 二、导出面

```ts
// 仓内出口（src/index.ts）
import {menuItems, routes, ComponentDocPage} from './src/index'

// 单个页面
import HomePage from './src/HomePage'
import ConsolePage from './src/ConsolePage'

// 组件层（vite alias @fc → ../z-surl-component/src/index.ts）
import {SurlListView, SurlShortenForm, SurlStatsRow} from '@fc'
import type {SurlItem, SurlShortenInput, SurlShortenResult} from '@fc'
```

`src/index.ts` 是本壳**唯一的 barrel**：把 `src/pages-manifest.tsx` 的清单整份转发，并让文档页以**统一名字**
`ComponentDocPage` 暴露出去（与其它仓「文档页统一叫 `ComponentDocPage`」的口径一致）。

## 三、API 端点

下表路径是 `src/api.ts` 里**实际调用**的路径。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/ctc/surl/shorten` | 生成短码，入参 `{originUrl, tenantCode, ttlMillis, remark}`，返回 `{mapKey, originUrl, expireAt, hits}` |
| GET | `/api/ctc/surl/info?mapKey=xxx` | 查询单条短链元信息；不存在抛 404 |
| DELETE | `/api/ctc/surl?mapKey=xxx` | 失效短链；不存在抛 404 |
| GET | `/s/{mapKey}` | **跳转面**（短码 → 原长链），不经 `/api` 前缀，由后端 302 |

> ⚠ `/api/ctc/surl/**` 里的 `ctc` 是**历史遗留路径**（z-surl 从 z-ctc 抽出时沿用）。
> z-opc 的 `SurlManagement` 控制台与部署健康检查都按这组路径发请求，**不要改**。

统一响应形如 `{data, success, code, message}`（后端 `Result`）；`src/api.ts` 的 `request` 在
`!res.ok || !body || body.success !== true` 时抛 `SurlApiError`（带 `code`）。

## 四、页面说明

### 4.1 首页

登录后着陆页。顶部欢迎语读 `localStorage.userInfo`，下方按 `menuItems`（剔除自身）渲染快捷入口卡片，点击即 `navigate(m.key)`。

### 4.2 短链控制台

一页四块：

- **统计行**（`SurlStatsRow`）：总条数 / 总命中 / 有效数 / 7 天内到期数 —— **只统计本次会话创建的短链**。
- **生成短链**（`SurlShortenForm`）：填原长链、租户、有效期档位、备注。
  生成接口只回 4 个字段，所以**再查一次 `info` 补全** `createdAt` / `tenantCode` / `remark`；
  补全失败**不阻断**（短码已生效，只是卡片少几个字段）。
- **按短码查询**：输入短码查元信息，`info` 抛 404 时提示「短码不存在或已失效」。
- **本次会话创建的短链**（`SurlListView`）：可复制短链、一键失效；空态走基座 `EmptyState`。

### 4.3 登录

**mock 登录**：硬编码 `admin / 123456`，成功后写 `localStorage.token`（值形如 `mock-admin`）与
`localStorage.userInfo`，再整页跳 `/z-surl/home`。CTC/SSO 接入后应换共享 `LoginPage` 的 `loginApi`。

### 4.4 组件文档

本文件。内容以 `?raw` 在编译期内联，零额外请求；左侧 240px TOC 随滚动联动。

## 五、配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `VITE_SURL_API_BASE` | 空（同源相对路径） | API 基础路径；只有「前端独立域名 + 直连后端」才需要设 |
| `VITE_SURL_PUBLIC_ORIGIN` | `window.location.origin` | 对外短链前缀，用于拼 `/s/{短码}` 完整链接 |
| dev proxy `/api` | `http://localhost:8080` | 管理面 |
| dev proxy `/s` | `http://localhost:8080` | 跳转面 |
| dev server port | `5174` | `vite.config.js` 的 `server.port` |
| vite `base` | `'/'` | **必须与 admin 的 `server.servlet.context-path` 一致**，否则页面能开但 `/assets/*` 全 404 |
| `LOCAL_SIBLINGS=1` | 关 | 开启后把 `@yuku123/z-surl-component` 别名到 `../z-surl-component/src` |
| token 存储键 | `token` | `localStorage` |
| userInfo 存储键 | `userInfo` | `localStorage`，JSON 串；解析失败降级为 `{name: raw}` |

## 六、已知限制

- **后端没有列表接口**（只有 shorten / info / invalidate / resolve 四个端点），
  所以控制台表格是**本次会话创建的短链**，不是全量库存，**刷新页面即清空**。
  要做真·全量列表需要后端补分页接口（`PaginationInnerInterceptor` 已装好）。
- **登录是硬编码 mock**（`admin / 123456`），没有真实鉴权；`App.tsx` 的 `ProtectedShell` 只判断 `localStorage.token` 是否存在。
- `src/api.ts` 的 `request` **不带任何鉴权头**（只发 `Content-Type`），也没有走基座的共享 request 实例。
- 生成接口只回 4 个字段，列表要展示全字段**必须多一次 `info` 往返**（已按「字段完整优先」取舍）。
- `npm run build` 会**先构建 `z-surl-component`**（`build:component` 脚本里带 `npm install`），
  所以本壳的构建**依赖组件层能独立构建通过**；单独构建本壳会在 `tsc --noEmit` 阶段就因 `@fc` 路径失败。
- vite `build.rollupOptions` **故意留空**：原先手工分 chunk 造成 vendor → antd → vendor 循环 chunk，
  浏览器端 TDZ 报 `Cannot access 'rn' before initialization`（构建绿但整页白屏），交给 rollup 自己分块。
- antd 6 全量 import 的 vendor chunk 约 1.17MB（gzip ~378KB），已把 `chunkSizeWarningLimit` 抬到 1300
  以免长期报警导致脱敏；真正瘦身要引入按需引入插件，属独立决策。

## 七、变更记录

| 版本 | 变更 |
|---|---|
| 1.0.0（未发布） | 新增组件文档页 `ComponentDocPage`（lead 008 §7）：`src/DOC.md` + 基座 `ComponentDoc` 薄包装 + 菜单末项 |
| 1.0.0 | 首版：短链控制台（生成 / 查询 / 失效 / 会话列表 / 统计）+ 首页 + mock 登录 + manifest |
