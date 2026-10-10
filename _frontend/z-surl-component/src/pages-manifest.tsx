/**
 * z-surl-component 路由清单（lead 005 §8.6 #3：./pages 命名导出 routes，非空数组）
 *
 * 占位说明：z-surl 的页面代码现仍住在 z-z-surl-suit/src/ 下（manifest + page 文件），
 * 本 manifest 现阶段只列骨架路由供主壳的 domainRoutes 探测；正式消费请走 suit。
 * 下次重构把 page 文件搬入 component 后，Component 字段直接换成同模块 import 即可。
 */
import { SurlListView } from '.';
import { SurlShortenForm } from '.';
import { SurlStatsRow } from '.';

export const appMeta = { title: 'z-surl 控制台', short: 'z-surl' }

export const routes = [
    { path: '/z-surl/list', title: '短链列表', order: 1, Component: SurlListView },
    { path: '/z-surl/shorten', title: '生成短链', order: 2, Component: SurlShortenForm },
    { path: '/z-surl/stats', title: '统计概览', order: 3, Component: SurlStatsRow },
]
