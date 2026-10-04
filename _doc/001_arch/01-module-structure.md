# 01 · 模块结构

## 三个 Maven 模块

| 模块 | packaging | 进 reactor | 上 Central | 谁 import |
|------|-----------|-----------|-----------|----------|
| `z-surl-core` | jar | ✅ | ✅ | 仅同仓 `z-surl-starter`（业务方不直接 import） |
| `z-surl-starter` | jar | ✅ | ✅ | **业务方唯一入口** |
| `z-surl-admin` | jar（repackage → `-exec.jar`） | ✅ | **❌** `maven.deploy.skip=true` | 没人 import，是 `java -jar` 入口 |

两个 npm 项目（`_frontend/*`）**不进 reactor**：纯 npm 产物，Maven 不需要介入。

## 依赖方向

```
z-surl-admin  ──▶  z-surl-starter  ──▶  z-surl-core
                                        （core 不依赖 starter，反向不允许）
```

`z-surl-core` 只被 starter 依赖，不依赖 starter 也不依赖 web 层 —— 这是"core 是业务方
不直接 import 的层"这条规则的机械体现：core 里出现任何 `web` / `spring-web` 的符号，
都说明分层破了。

## 包结构

### z-surl-core（`com.zifang.z.surl`）

```
domain/entity/SurlMappingDO          @TableName("z_surl_mapping")，表名走 TABLE 常量
domain/mapper/SurlMappingMapper      BaseMapper + incrementHits（原子 +1）
domain/service/SurlDbService(Impl)   数据访问层（单测靠代理整层替换掉）
service/SurlService(Impl)            业务层：生码/解析/命中/失效
```

**为什么多一层 `SurlDbService`**：为了让 `SurlServiceMySqlImpl` 的单测能用 JDK 动态代理
把数据访问整层替换成内存 Map（0 Mockito / 0 DB，007 步骤 8）。业务方换 Redis / in-memory
实现时也只换这一层。

### z-surl-starter（`com.zifang.z.surl`）

```
autoconfigure/ZSurlAutoConfiguration  @AutoConfiguration + 独立数据源 + 独立 SqlSessionFactory
web/SurlController                    4 个端点
web/SurlMappingVO                     管理面视图对象
web/request/SurlShortenRequest        入参
web/response/SurlShortenResponse      出参
resources/META-INF/spring.factories   自动装配注册
```

> 按 005 §2.5，starter 自带的 web 层（Controller + DTO）是 starter 的一部分，同样发 Central。

### z-surl-admin（`com.zifang.z.surl.admin`）

```
SurlAdminApplication    bare @SpringBootApplication（**刻意不带 scanBasePackages**）
config/DevDataSourceConfig  @Profile("dev")：H2 内存库 + 建表脚本
resources/application.yml    z.base.db.surl.* + 凭证占位符
resources/db/h2/schema.sql   H2 建表（dev profile 用）
```

**为什么启动类是 bare 的**：写了 `scanBasePackages = "com.zifang.z.surl"` 会与 starter 里
`@ComponentScan` 叠加，同一批 bean 注册两次 → `NoUniqueBeanDefinitionException`。
starter 的 `@ComponentScan` 已显式列出三个包，启动类只扫 `admin` 自己的空壳包。

**为什么 `@ComponentScan` 列三个包而不是 `"com.zifang.z.surl"`**：
`z-surl-admin` 就在这个包路径下，宽包扫描会把 `@SpringBootApplication`（隐含 `@Configuration`）
也注册成 bean。列包即免疫。

## 前端两层

| 层 | npm 名 | 产物 | 职责 |
|----|--------|------|------|
| 组件层 | `@yuku123/z-surl-frontend-component` | `dist/index.js` + `.d.ts` | 纯展示组件，**只接 props 不 fetch**（005 §5） |
| 应用层 | `@yuku123/z-surl-frontend`（private） | `dist/` | SPA，负责取数与组装，嵌进 admin jar |

组件层消费的符号：

```
SurlStatsRow      顶部 KPI
SurlShortenForm   生成短链表单（有效期用预设档位，不让用户填毫秒）
SurlListView      短链表格（复制 / 打开 / 失效）
SURL_TTL_PRESETS  有效期预设（0 = 永不过期，与后端 computeExpireAt 约定一致）
SurlItem / SurlShortenInput / SurlShortenResult
```

**依赖方向**：应用层 → 组件层（`file:` 协议 + `@fc` alias + `--install-links` 软链）。
组件层不知道 HTTP 存在，因此可以被接 mock、接 z-opc、接任何数据源。

## 换号记录（2026-10-04）

| 项 | 换号前 | 换号后 |
|----|--------|--------|
| Maven 坐标 | `com.zifang:z-tool-curl` | `io.github.yuku123:z-surl-{core,starter,admin}` |
| parent | `com.zifang:z-tool:1.0.0-SNAPSHOT` + `relativePath=../pom.xml`（**该文件不存在**） | `z-boot-parent:1.0.21` + `relativePath` 留空 |
| 版本 | `1.0.0-SNAPSHOT` | `${revision}` = `1.0.0` + flatten(oss) |
| 包名 | `com.zifang.z.tool.curl` | `com.zifang.z.surl` |
| 实现类 | `CtcSurlServiceMySqlImpl` | `SurlServiceMySqlImpl` |
| 自动配置 | `ZToolCurlAutoConfiguration` | `ZSurlAutoConfiguration` |
| bean 名 | `dataSourceCurl` / `sqlSessionFactoryCurl` | `dataSourceSurl` / `sqlSessionFactorySurl` |
| 配置前缀 | `z.base.db.curl.*` | `z.base.db.surl.*` |
| 表名 | `z_ctc_surl_mapping` | `z_surl_mapping` |

**未换的一项**：HTTP 路径 `/api/ctc/surl/**` 保持不变 —— 有线上控制台与部署健康检查
两处活依赖，详见 `02-feature.md` 风险表第 1 行。
