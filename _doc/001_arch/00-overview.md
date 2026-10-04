# 00 · 总览

## 这是什么

z-surl 是 z-opc-foundation 组织的 **L3 业务型中间件**，提供短网址能力：
短码生成 → 解析跳转 → 命中统计 → 失效。业务方一行 `z-surl-starter` 依赖即可获得
一套带独立数据源、带控制台的短链服务。

## 在组织里的位置

```
L2 z-boot（BOM 版本权威）        z-boot-parent 1.0.21 / z-boot-dependencies / z-boot-fleet
        ▲ import
L3 z-surl（本仓）                z-surl-core + z-surl-starter (+ z-surl-admin 不发 Central)
        ▲ 被 import
L1 z-opc（业务产品）             主后端 + 主控制台
```

**依赖方向**（006 §2.3 铁律）：z-surl 只依赖 L2（z-boot BOM）与同仓模块；
**禁止**依赖任何 L1（z-opc monorepo）内部坐标 —— 外部开发者用不起来。

## 技术栈（与组织其余 32 个 Java 仓一致）

| 维度 | 取值 | 来源 |
|------|------|------|
| Java | 8（字节码 target 8） | parent 的 pluginManagement |
| Spring Boot | 2.7.18 | `z-boot-dependencies` 地板 |
| ORM | MyBatis-Plus 3.5.7 | 地板（mybatis 3.5.16 / jsqlparser 4.9 配套） |
| 连接池 | Druid 1.2.23 | `z-boot-datasource-starter` |
| parent | `io.github.yuku123:z-boot-parent:1.0.21` | 见下方选型依据 |
| namespace | `io.github.yuku123` | Sonatype 验证过，`com.zifang` 不再用 |
| 日志 | Log4j2 2.25.4 | 地板 |
| 前端 | React 19.2 + Vite 6.2 + antd 6.3 + TS 5.6 | 组织最新基线（z-oss / z-meta / z-config 一致） |
| 测试 | JUnit 5 + JDK 动态代理，0 Mockito / 0 DB | 007 步骤 8 |
| 发布 | Maven Central（admin 除外） | 004 flatten 规范 |

### 为什么父链选 1.0.21

全组织 34 个 Java 仓里 **32 个用 1.0.21**，只有 z-opc 和 z-rpa 用 1.0.23。
按"和其他的一样"的口径取 1.0.21（即多数派，也是本次实测离线可解析的版本）。
若将来 1.0.23 成为新地板，全组织一起 bump，不要单仓先行。

## 启动时序

```
Spring Boot 启动
  ├─ spring.factories → ZSurlAutoConfiguration
  │    ├─ dataSourceSurl        ← ModuleDataSourceTemplate.buildDataSource(env, "surl")
  │    ├─ mybatisPlusInterceptorSurl
  │    ├─ sqlSessionFactorySurl ← 注入上面那个 dataSourceSurl
  │    ├─ @MapperScan(z-surl.domain.mapper, sqlSessionFactoryRef=sqlSessionFactorySurl)
  │    └─ @ComponentScan(web / service.impl / domain.service.impl)
  └─ dev profile 时：
       DevDataSourceConfig.dataSourceSurl  ← 先于自动装配注册，条件装配主动退让
```

**关键点**：`dataSourceSurl` 在 dev profile 下由 admin 自己的 `@Bean("dataSourceSurl")` 提供，
自动装配里的同名 bean 因 `@ConditionalOnMissingBean(name="dataSourceSurl")` 退让，
而 `sqlSessionFactorySurl` 是按名注入，自动接到 H2 上。详见 `DevDataSourceConfig` 注释。

## 边界

- 本仓**不连生产库**，也不代跑任何 DDL。表改名脚本只生成、不执行。
- 凭证只走环境变量 / k8s Secret，永不进 Git（008 凭证管理规范）。
- admin 是可运行 demo + 镜像源，**永远不上 Maven Central**（005 §2.2）。
