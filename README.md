# z-surl

> 短网址服务 —— 短码生成 / 解析 / 命中统计 / 失效。Spring Boot Starter 一行集成，自带独立数据源与管理控制台。
>
> 组织定位：**L3 业务型中间件**（`lead/005_技术架构/006_整体架构与新仓接入.md` §2.3）。
> 只依赖 L2（z-boot BOM）+ 同仓模块，不依赖 z-opc monorepo 内部坐标。

## 一句话技术栈

**Java 8 + Spring Boot 2.7.18 + MyBatis-Plus 3.5.7 + z-boot-parent 1.2.2 + Maven Central（`io.github.yuku123`）**
前端 **React 19 + Vite 6 + antd 6 + TypeScript**。

## 目录结构

```
z-surl/
├── pom.xml                    # 自包含 parent（${revision} + flattenMode=oss）
├── z-surl-core/               # SurlMappingDO / SurlMappingMapper / SurlDbService / SurlService（业务方不直接 import）
├── z-surl-starter/            # 业务方 import 的唯一入口：ZSurlAutoConfiguration（@AutoConfiguration）+ SurlController（HTTP API）
├── z-surl-admin/              # 最小可启动单体（deploy.skip=true，**不上 Central**）
├── _frontend/                 # 前端容器
│   ├── z-surl-component/   # 组件层（library，纯 props 契约，可跨仓复用）
│   └── z-surl-suit/             # 应用层（SPA，只服务本仓 admin）
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
    <!-- 版本不写：由 z-boot-parent 锁 -->
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

> 本仓 README 的定量主张（模块清单 / admin 不上 Central / 端口 / HTTP 端点数 / 表名 /
> 风险条数 / 架构篇数）由 `z-surl-core/src/test/java/com/zifang/z/surl/ReadmeClaimsTest.java`
> 逐条与源码对账，README 改了就红。

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
make -f deploy/Makefile test     # 单测（`SurlServiceMySqlImplTest` + `SurlControllerValidationTest`
                              #       两个测试类，JDK 动态代理 + 内存 Map，0 Mockito / 0 DB）
make -f deploy/Makefile verify   # 收口验证：单测 + house-style 闸门 + 依赖树无 com.zifang
make -f deploy/Makefile fe-dev   # 前端热更新
```

## 已知风险

见 [`_doc/001_arch/02-feature.md`](_doc/001_arch/02-feature.md) 的风险表（6 条，含缓解措施）。

## License

[MIT](LICENSE)

<!-- icon: minimax image-01, gradient=#0891b2, glyph=url -->

## 文档目录

本项目文档统一收口在 `_doc/` 下（lead 002 规范八类固定编号表，桶按需存在）:

- [`_doc/001_arch/`](_doc/001_arch/) — 项目总览、模块结构、接口清单、DB schema
- [`_doc/002_deploy/`](_doc/002_deploy/) — 初始化 SQL（`init/schema.sql` 与 `init/migration/`）
- [`_doc/003_script/`](_doc/003_script/) — 房规门禁脚本（`house-style-gate.sh`）

---

<!-- z-opc-foundation-sibling-registry:v1 -->
## 🔗 兄弟仓全景（z-opc-foundation 组织）

本仓隶属于 [z-opc-foundation](https://github.com/orgs/z-opc-foundation/repositories) 组织——一人公司基座的全部自研件（现存 61 仓，含私有仓；口径 2026-11-24 GitHub org API 实读）。

| 仓 | 一句话说明 |
|---|---|
| [z-agent](https://github.com/z-opc-foundation/z-agent) | 云端 Agent 平台的库与 starter——ReAct/Plan 实现 + 注册中心 + REST 网关 + 只读控制面 |
| [z-agent-kernel](https://github.com/z-opc-foundation/z-agent-kernel) | Agent 框架基座——消息/工具/记忆/provider/会话 SPI + 轻量默认实现（纯 POJO 无 Spring） |
| [z-asset](https://github.com/z-opc-foundation/z-asset) | 企业资产台账——实物设备全生命周期 + 域名/证书/许可到期提醒 |
| [z-audit](https://github.com/z-opc-foundation/z-audit) | 审计与合规留痕——事件模型 + 哈希链 + 事件出口 SPI + 留存期策略 |
| [z-biz](https://github.com/z-opc-foundation/z-biz) | 商务外壳域——客户/合同/票据/对账四聚合 |
| [z-biz-erp](https://github.com/z-opc-foundation/z-biz-erp) | 进销存 ERP——商品/客商/采购/销售/库存流水/应收应付核销（z-biz 业务线） |
| [z-biz-security](https://github.com/z-opc-foundation/z-biz-security) | 业务数据安全防护入口——场景化策略注册 + 安全线四段流水线对外编排 |
| [z-boot](https://github.com/z-opc-foundation/z-boot) | Spring Boot Starter 聚合仓 + 版本权威（火车制）——唯一消费入口 z-boot-parent |
| [z-bot](https://github.com/z-opc-foundation/z-bot) | 本地 agent 应用——终端 CLI + HTTP/SSE web 控制台 + 多通道 gateway（飞书/钉钉/webhook） |
| [z-cache](https://github.com/z-opc-foundation/z-cache) | RESP2 协议兼容的分布式内存缓存——Java 8 + Netty，Redis 客户端零修改直连 |
| [z-camuda](https://github.com/z-opc-foundation/z-camuda) | 【已退役·归档】基于 Camunda 的工作流封装层（原 z-wf 改名，与现行 z-wf 无共同历史） |
| [z-cognition](https://github.com/z-opc-foundation/z-cognition) | 业务认知域/认知舱——术语/概念/证据链 + memory/kb/ontology 渐进式模块 |
| [z-config](https://github.com/z-opc-foundation/z-config) | 分布式配置中心 + 服务注册中心（类 Nacos）——Netty 长连接推送 + React 控制台 |
| [z-ctc](https://github.com/z-opc-foundation/z-ctc) | 4A 中心——认证/授权/账户/审计，多租户 JWT + SSO 跨子域 |
| [z-dms](https://github.com/z-opc-foundation/z-dms) | 数据源管控底座——统一登记、统一建池、统一探活、变更统一推送 |
| [z-env](https://github.com/z-opc-foundation/z-env) | 本地中间件与基础设施环境——31 个 docker-compose 场景 + make 一键运维 |
| [z-etl](https://github.com/z-opc-foundation/z-etl) | ETL 引擎——DataX 式线性作业 + Flink 式 DAG 流管道（键控状态/水位/窗口） |
| [z-ext](https://github.com/z-opc-foundation/z-ext) | Java 扩展点框架——@ExtPoint/@ExtImpl 运行期选实现，五档侵入方式 |
| [z-frontend-common](https://github.com/z-opc-foundation/z-frontend-common) | 全仓共享前端基座——AppLayout/LoginPage 等前端规范的唯一承载 |
| [z-frontend-markdown](https://github.com/z-opc-foundation/z-frontend-markdown) | Markdown 通用共享组件包（npm）——MarkdownLiveEditor/NoteMarkdown/MermaidBlock 单一真源 |
| [z-frontend-render](https://github.com/z-opc-foundation/z-frontend-render) | 平台共享前端渲染引擎——后端 schema 是唯一事实源，报表与低代码共用 |
| [z-graph](https://github.com/z-opc-foundation/z-graph) | 独立图数据库——MVCC 版本链存储 + OpenCypher 子集 + Bolt 风格协议 + React 控制台 |
| [z-gw](https://github.com/z-opc-foundation/z-gw) | 自研高性能 API 网关——Netty 手写运行时，Route/Predicate/Filter 模型可嵌 Spring Boot |
| [z-indexer](https://github.com/z-opc-foundation/z-indexer) | 代码索引服务——仓库注册表 + 定时重建索引 + 符号/调用链查询 |
| [z-introduce](https://github.com/z-opc-foundation/z-introduce) | 组织仓库展示站——全仓实测元数据汇总成可搜索静态站点 |
| [z-lc](https://github.com/z-opc-foundation/z-lc) | 低代码平台——DB 驱动通用 CRUD 引擎 + 报表/模型设计框架 |
| [z-llm-agent](https://github.com/z-opc-foundation/z-llm-agent) | CLI Agent 服务化——把本机 CLI agent（CC/Hermes/Qoder 等）暴露成 OpenAI 兼容对话 API |
| [z-llm-gateway](https://github.com/z-opc-foundation/z-llm-gateway) | LLM 网关——OpenAI/Anthropic 双协议面 + 多 vendor 凭据池、限流与 token 记账 |
| [z-llm-msghub](https://github.com/z-opc-foundation/z-llm-msghub) | msgHub 中心消息路由——WebSocket 中心枢纽 + Agent 侧客户端 SDK |
| [z-mask](https://github.com/z-opc-foundation/z-mask) | 脱敏与遮蔽引擎——按敏感命中位置遮蔽，策略即数据 |
| [z-mcp](https://github.com/z-opc-foundation/z-mcp) | MCP 服务端 + 上游聚合网关——Java 8 手写协议层，Spring Boot 自动装配 |
| [z-meta](https://github.com/z-opc-foundation/z-meta) | 元数据管理平台——租户/应用/接口清单登记 + @BizModel 业务模型采集 |
| [z-mill](https://github.com/z-opc-foundation/z-mill) | 资料磨坊——全格式文档转 Markdown/JSON/纯文本/HTML（对标 MinerU） |
| [z-mist](https://github.com/z-opc-foundation/z-mist) | KMS 分布式密钥/秘密管理平台——集中加密存储 + Netty 协议 + starter 嵌入 |
| [z-mq](https://github.com/z-opc-foundation/z-mq) | 自研分布式消息队列——NameServer+Broker+Store，形状对标 RocketMQ、自有 Netty 协议 |
| [z-msg](https://github.com/z-opc-foundation/z-msg) | 多通道消息 + WebSocket 实时下发基础设施——短信/邮件/站内信/Webhook/IM 平台 |
| [z-observer](https://github.com/z-opc-foundation/z-observer) | 可观测性域——指标/日志/链路 + 告警引擎 |
| [z-opc](https://github.com/z-opc-foundation/z-opc) | 一人公司统一后端+统一前端——单 JVM all-in-one 承载协作/Agent/RPA/交易/资产等业务域与自研中间件 |
| [z-opc-foundation-lead](https://github.com/z-opc-foundation/z-opc-foundation-lead) | 组织治理大脑——事实表、强制规范、跨仓工具集、新机器恢复钥匙（本清单权威持有处） |
| [z-opcs](https://github.com/z-opc-foundation/z-opcs) | 任务撮合/服务市场——用户付费发布诉求，持有者接单完成，金额自动瓜分 |
| [z-ops](https://github.com/z-opc-foundation/z-ops) | 个人公司运维平台——部署单元/镜像构建/WebShell/K8s 巡检 + Ops Agent 执行器 |
| [z-oss](https://github.com/z-opc-foundation/z-oss) | 对象存储服务——自持 local 后端 + aliyun/s3 对接层，starter 注入 OssTemplate |
| [z-product](https://github.com/z-opc-foundation/z-product) | 产品设计信息汇总仓（开发域 PM 工件） |
| [z-qa](https://github.com/z-opc-foundation/z-qa) | 步骤式 API 链路测试平台——用例/计划/执行/报告/质量看板（对标 TestRail·MeterSphere） |
| [z-rpa](https://github.com/z-opc-foundation/z-rpa) | RPA 产品皮——应用市场/需求中心（执行归 z-script，触发归 z-schedule） |
| [z-rpc](https://github.com/z-opc-foundation/z-rpc) | Java 8 + Netty RPC 框架——微内核 + SPI 插件化，形状参照 Dubbo/gRPC |
| [z-schedule](https://github.com/z-opc-foundation/z-schedule) | 分布式任务调度中枢（对标 XXL-Job）——选主 + 时间环排期，starter 嵌入宿主 |
| [z-script](https://github.com/z-opc-foundation/z-script) | 多 DSL 脚本平台 + Mock 平台——沙箱执行/版本灰度/可注册 MCP Tool，starter 内嵌 |
| [z-security](https://github.com/z-opc-foundation/z-security) | 数据安全域编排层——场景→策略→识别→脱敏→水印→审计 四段流水线编排 |
| [z-sense](https://github.com/z-opc-foundation/z-sense) | 敏感识别与分类分级引擎——数据安全线底座（规则目录 + 可插拔识别器 + 分类分级） |
| [z-skill](https://github.com/z-opc-foundation/z-skill) | Skill 平台聚合器——归一化各家 Skill 形状，再按各家 API 形状发回 |
| [z-surl](https://github.com/z-opc-foundation/z-surl) | 短网址服务——短码生成/解析/命中统计，starter 一行集成 **（本仓）** |
| [z-task](https://github.com/z-opc-foundation/z-task) | 任务协作平台——项目空间/看板/任务卡片（看板范式，对标 Trello·Teambition） |
| [z-team](https://github.com/z-opc-foundation/z-team) | 数字员工协同域——全栈独立仓，自 z-opc 毕业 |
| [z-trade](https://github.com/z-opc-foundation/z-trade) | 交易域独立仓——自 z-opc reactor 迁出 |
| [z-util](https://github.com/z-opc-foundation/z-util) | 底层 Java 工具库——89 个模块 POM，Java 8/17 口径，按需引用按需升级 |
| [z-vector](https://github.com/z-opc-foundation/z-vector) | 自研 Java 向量数据库——嵌入式+可独立部署，ANN+Payload 过滤，兼容 Qdrant REST |
| [z-voice-demo](https://github.com/z-opc-foundation/z-voice-demo) | 语音链路 demo——流式识别→kernel LLM→流式 TTS→可打断 的契约验证实现 |
| [z-watermark](https://github.com/z-opc-foundation/z-watermark) | 水印引擎——显式文字/图片隐写/盲水印，三种技术成对「打+验」 |
| [z-webide](https://github.com/z-opc-foundation/z-webide) | 企业级 WebIDE 后端——Git 仓/共享目录秒变 code-server 实例，全生命周期管理 |
| [z-wf](https://github.com/z-opc-foundation/z-wf) | 自研流程引擎——不依赖 Camunda/Flowable，定义层与运行时全自研 |

**已退役仓**（迁至 [z-trash](https://github.com/z-trash) 组织，archived 只读）：

| 仓 | 说明 |
|---|---|
| [z-agent-proxy](https://github.com/z-trash/z-agent-proxy) | 三方 coding agent 适配代理（Aider/Cursor → kernel Agent SPI），2026-10-26 退役 |
| [z-kb](https://github.com/z-trash/z-kb) | 自研知识库系统（对标 LightRAG/RAGFlow），2026-11 并入 z-cognition 认知舱后退役 |
| [z-ontology](https://github.com/z-trash/z-ontology) | 本体层（TBox/ABox/RDFS 推理），并入 z-cognition 后退役 |

另有 [z-report](https://github.com/z-opc-foundation/z-report)（报表域，2026-10-11 撤销并入 z-lc（远端已删除））。

总清单权威持有处在 [z-opc-foundation-lead](https://github.com/z-opc-foundation/z-opc-foundation-lead) `008_组织规范/005_兄弟仓清单.md`。
