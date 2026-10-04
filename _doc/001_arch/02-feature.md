# 02 · 功能矩阵与已知风险

## 功能矩阵

| 能力 | 端点 / 入口 | 实现位置 | 测试覆盖 |
|------|------------|---------|---------|
| 生成短码 | `POST /api/ctc/surl/shorten` | `SurlServiceMySqlImpl#shorten` | ✅ 含冲突重试、TTL 换算、参数校验 |
| 短码冲突重试 | 内部（SELECT 探测 + 最多 5 次） | `generateUniqueKey` | ✅ 正例（重试成功）+ 反例（打满抛异常） |
| 解析跳转 | `GET /s/{mapKey}` → 真 302 | `SurlServiceMySqlImpl#resolve` | ✅ 含过期、空短码、未知短码 |
| 命中统计 | 跳转时原子 +1 | `SurlMappingMapper#incrementHits` | ✅ 含"过期不再计数"断言 |
| 元信息查询 | `GET /api/ctc/surl/info` | `SurlService#info` | ✅ 断言只读（不 +1） |
| 失效 | `DELETE /api/ctc/surl` | `SurlServiceMySqlImpl#invalidate` | ✅ 含重复失效 |
| 租户强制匹配 | `GET /s/{mapKey}?tenant=` | `SurlController#resolve` | ❌ 未覆盖（见风险 6） |
| 独立数据源 | `z.base.db.surl.*` | `ZSurlAutoConfiguration` | ❌ 需真实 DB |
| 控制台 | `/` | `_frontend/z-surl-frontend` | ❌ 未覆盖（见风险 5） |

单测：**20 例，0.06s**，纯 JUnit 5 + JDK 动态代理 + 内存 Map，0 Mockito / 0 DB / 0 `@SpringBootTest`。

端到端已实测（dev profile + H2，见风险表外的"实测记录"）：生成 → 查询 → 两次跳转 hits=2
→ 租户不匹配 404 → 失效 → 跳转 404 → 重复失效 404。

## 已知风险

| # | 风险 | 影响 | 缓解 / TODO |
|---|------|------|-----------|
| 1 | HTTP 路径带 `ctc` 前缀（`/api/ctc/surl/**`），与仓名 z-surl 不符 | 改名会同时打断两处活依赖：z-opc 线上控制台 `SurlManagement/api.js`、250 部署健康检查 `curl .../api/ctc/surl/shorten` | **本次不改**（007 步骤 6「URL/param 保持不变」）。要改必须同一次变更里改上述两处 + 控制台配置，改完补一条 e2e 断言健康检查仍通 |
| 2 | 管理面端点的错误**只体现在 body 的 `code` 字段，HTTP 状态码仍是 200**；而 `resolve` 用真 302/404 —— 两个风格不一致 | 按 HTTP 状态码做监控/告警、CDN 缓存、浏览器 devtools 都看不出失败 | 本次不改：z-opc 的 `common/utils/request.ts` 目前按 body 的 `success` 分流，改成真状态码会改变它 404/403 分支走的文案，而那套前端不在本仓、无法验证。建议改动时前后端一起动：后端 `Result` 改用 `ResponseEntity` 透传，前端拦截器已写明"优先看 success，code 恢复纯 HTTP 语义不必再改" |
| 3 | 后端**没有列表接口**，控制台表格只显示"本次会话创建"的条目，刷新即清空 | 不是全量库存，无法做批量运维 | 组件层与后端都已留好位置：`PaginationInnerInterceptor` 已装、`SurlListView` 是纯 props 组件。下一步补 `GET /api/ctc/surl/list?page=&size=`，admin 侧把会话数组换成接口数据即可，组件不用改 |
| 4 | `ModuleDataSourceTemplate` 在 `z.base.db.surl.*` 配错时**静默回落到 `z.base.db.default.*`** | 多租户场景下配错模块前缀会查错库，且没有任何报错 | 当前 dev 与 k8s 都显式写全 5 个键来规避。根治需在 `ZSurlAutoConfiguration` 里对生效的数据源名打启动日志（已记 TODO） |
| 5 | 单测只覆盖 Service 层，Controller 与前端零覆盖 | Controller 字段映射漂移、JSON 字段名不匹配都无拦截 | 下一步用 `@WebMvcTest` 补 controller 切片测（注意：这是唯一允许 `@SpringBootTest` 家族的场景，house-style 闸门第 3 条需相应放开测试目录） |
| 6 | 租户匹配对**公共链接（`tenant_code IS NULL`）不生效**：`resolve` 的判断是 `record.getTenantCode() != null && !equals(tenant)`，租户为 null 时整段短路 | 公共短链带任意 `?tenant=` 参数都能正常跳转。若将来把公共链接当作"必须不带租户参数才能访问"，当前实现不满足 | 语义上是"公共链接本就公开"，所以现状自洽。但这是**隐式**语义：代码读起来像"没检查"。建议要么在需求里明确公共链接不受 tenant 约束，要么改成显式判断并在 javadoc 写清 |

## 实测记录（2026-10-04，dev profile + H2）

| 步骤 | 期望 | 实测 |
|------|------|------|
| `GET /actuator/health` | `{"status":"UP"}` | ✅ |
| `GET /` | 200 text/html（前端已内嵌） | ✅ 558 B |
| `GET /assets/index-*.js` | 200 | ✅ |
| `POST /api/ctc/surl/shorten` | body `code=201`，8 字符 Base62 短码 | ✅ `8xaZm0mi` |
| `GET /api/ctc/surl/info` | 全字段回显（含 createdAt/createdBy/remark） | ✅ |
| `GET /s/{key}` | 302 + `Location` | ✅ |
| 跳转 2 次后查 info | `hits = 2` | ✅ |
| `GET /s/{key}?tenant=other` | 404 | ✅ |
| `POST` 空 `originUrl` | body `code=400` | ✅（HTTP 状态见风险 2） |
| `POST` 无 body | body `code=400` | ✅ |
| `ttlMillis=0` | `expireAt` 缺省（永不过期） | ✅ |
| `DELETE` 后跳转 | 404 | ✅ |
| 重复 `DELETE` | body `code=404` | ✅ |

## TODO（不在本次技术栈对齐范围内）

1. 补列表接口 + admin 侧接真实数据（风险 3）
2. 启动日志打印生效的数据源名（风险 4）
3. `@WebMvcTest` controller 切片测（风险 5）
4. 决策：管理面是否切真 HTTP 状态码（风险 2，需前后端同一次变更）
5. 决策：公共链接的 tenant 语义（风险 6）
