# z-surl

> 短网址服务 —— 短码生成 / 解析 / 命中统计 / 失效。Spring Boot Starter 一行集成，自带独立数据源与管理控制台。
>
> 组织定位：**L3 业务型中间件**（`lead/005_技术架构/006_整体架构与新仓接入.md` §2.3）。
> 只依赖 L2（z-boot BOM）+ 同仓模块，不依赖 z-opc monorepo 内部坐标。

## 一句话技术栈

**Java 8 + Spring Boot 2.7.18 + MyBatis-Plus 3.5.7 + z-boot-parent 1.0.21 + Maven Central（`io.github.yuku123`）**
前端 **React 19 + Vite 6 + antd 6 + TypeScript**。

## 目录结构

```
z-surl/
├── pom.xml                    # 自包含 parent（${revision} + flattenMode=oss）
├── z-surl-core/               # DO / Mapper / SurlDbService / SurlService（业务方不直接 import）
├── z-surl-starter/            # 业务方 import 的唯一入口：@AutoConfiguration + HTTP API
├── z-surl-admin/              # 最小可启动单体（deploy.skip=true，**不上 Central**）
├── _frontend/                 # 前端容器
│   ├── z-surl-frontend-component/   # 组件层（library，纯 props 契约，可跨仓复用）
│   └── z-surl-frontend/             # 应用层（SPA，只服务本仓 admin）
├── deploy/                    # 三模式 compose + Dockerfile ×2 + k8s + Makefile
└── _doc/
    ├── 001_arch/              # 架构四篇（overview / module-structure / feature / db-schema）
    ├── 002_deploy/init/       # 权威 DDL + 迁移脚本
    └── 003_script/            # house-style 闸门
```

## 快速开始

### 本机直跑（零外部依赖，H2 内存库）

```bash
make -f deploy/Makefile build
java -jar z-surl-admin/target/z-surl-admin-*-exec.jar --spring.profiles.active=dev
```

| 地址 | 说明 |
|------|------|
| <http://localhost:8080> | 控制台（前端已内嵌进 jar） |
| <http://localhost:8080/actuator/health> | 健康检查（compose / k8s 探针打这里） |

> 端口 **8080** 在 `application.yml` / `Dockerfile EXPOSE` / compose / k8s `containerPort` / 本文
> 五处是同一个值。改端口必须五处同改（007 踩坑 #19：不一致会全站 502）。

### Docker

```bash
cp deploy/env/.env.example deploy/env/.env   # 改数据库参数（凭证不入 Git）
make -f deploy/Makefile fe                   # 构建前端两层
make -f deploy/Makefile dev                  # Mode 1 合体（单容器）
make -f deploy/Makefile split                # Mode 2 分体（前端独立 nginx，:8088）
make -f deploy/Makefile cluster              # Mode 3 集群（1 前端 + 3 后端）
make -f deploy/Makefile down                 # 停掉
```

### k8s

```bash
read -rs ZSURL_DB_PASSWORD && export ZSURL_DB_PASSWORD
make -f deploy/Makefile k8s-apply
```

Secret 现场生成，**不从文件 apply**（`deploy/k8s/06-secret.example.yaml` 只是模板）。

## 接入（业务方）

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-surl-starter</artifactId>
    <!-- 版本不写：由 z-boot-dependencies / z-boot-fleet 锁 -->
</dependency>
```

```yaml
z:
  base:
    db:
      surl:                      # 短链的独立数据源，与 z.base.db.ctc.* 平行但互不复用
        host: ${ZSURL_DB_HOST}
        port: ${ZSURL_DB_PORT:3306}
        database: ${ZSURL_DB_NAME:oc}
        username: ${ZSURL_DB_USER:root}
        password: ${ZSURL_DB_PASSWORD:}
```

> `ModuleDataSourceTemplate` 只读 `host/port/username/password/database` 这几个键，
> **自己拼 `jdbc:mysql://` URL，没有 `url` 配置项**，写了不生效。

## HTTP API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ctc/surl/shorten` | 生成短码（201） |
| GET | `/api/ctc/surl/info?mapKey=` | 查元信息（不存在 → body `code=404`） |
| DELETE | `/api/ctc/surl?mapKey=` | 失效（不存在 → body `code=404`） |
| GET | `/s/{mapKey}` | 跳转（**真 302** + `Location`；不存在 → 真 404） |

### ⚠️ 路径里的 `ctc` 是历史遗留，不要改

本仓从 z-ctc 抽出时沿用了 `/api/ctc/surl/**`，与新仓名 z-surl 不符。但实测有**两处活依赖**：

1. `z-opc/bootstraps/z-opc-main-starter-frontend/src/ctc/components/SurlManagement/api.js` —— 线上控制台按这组路径发请求；
2. `z-opc/_doc/005_skills/z-opc-deploy-250/` 的部署健康检查：`curl http://192.168.31.250:8888/api/ctc/surl/shorten`。

改路径会让控制台 404、健康检查判死。要收敛成 `/api/zSurl/**`，必须**同一次变更**里改上面两处。
详见 `SurlController` 类注释与 `_doc/001_arch/02-feature.md` 风险表第 1 行。

## 表

单表 `z_surl_mapping`（原名 `z_ctc_surl_mapping`，本仓独立后换名）。
权威 DDL：`_doc/002_deploy/init/schema.sql`；
线上改名脚本：`_doc/002_deploy/init/migration/001_rename_z_ctc_surl_mapping.sql`
——**由 DBA 择机执行，本仓不代跑 DDL**。

## 开发

```bash
make -f deploy/Makefile test     # 单测（20 例，JDK 动态代理 + 内存 Map，0 Mockito / 0 DB）
make -f deploy/Makefile verify   # 收口验证：单测 + house-style 闸门 + 依赖树无 com.zifang
make -f deploy/Makefile fe-dev   # 前端热更新
```

## 已知风险

见 [`_doc/001_arch/02-feature.md`](_doc/001_arch/02-feature.md) 的风险表（5 条，含缓解措施）。

## License

[MIT](LICENSE)
