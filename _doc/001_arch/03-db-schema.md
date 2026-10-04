# 03 · 表结构

## 权威源

| 文件 | 用途 | 面向 |
|------|------|------|
| `_doc/002_deploy/init/schema.sql` | **唯一 DDL 权威源** | MySQL 8（生产） |
| `_doc/002_deploy/init/migration/001_rename_z_ctc_surl_mapping.sql` | 表改名（RENAME + 索引） | MySQL 8，由 DBA 执行 |
| `z-surl-admin/src/main/resources/db/h2/schema.sql` | H2 等价物 | dev profile 内存库 |

仓内**没有**第二份整建副本（007 踩坑 #14：两份互相覆盖、字段漂移）。

## `z_surl_mapping`

| 列 | 类型 | 空 | 实体字段 | 说明 |
|----|------|----|---------|------|
| `id` | BIGINT AUTO_INCREMENT | NOT NULL | `Long id` | 主键 |
| `map_key` | VARCHAR(16) | NOT NULL | `String mapKey` | 短码，Base62 8 字符 |
| `origin_url` | VARCHAR(2048) | NOT NULL | `String originUrl` | 原长链接 |
| `tenant_code` | VARCHAR(64) | NULL | `String tenantCode` | 归属租户，NULL = 公共 |
| `expire_at` | DATETIME | NULL | `LocalDateTime expireAt` | NULL = 永不过期 |
| `hits` | BIGINT | NOT NULL DEFAULT 0 | `Long hits` | 跳转命中次数 |
| `created_at` | DATETIME | NOT NULL | `LocalDateTime createdAt` | 创建时间，**由业务层写**（不依赖 DB default） |
| `created_by` | VARCHAR(64) | NULL | `String createdBy` | 创建人 |
| `remark` | VARCHAR(512) | NULL | `String remark` | 备注 |

索引：

| 索引 | 列 | 为什么 |
|------|----|--------|
| `uk_z_surl_mapping_map_key` (UNIQUE) | `map_key` | **业务唯一键**。短码生成靠"SELECT 不存在才插入"避让，并发下两个请求可能拿到同一短码；没有唯一索引，重复短码会静默入库，之后按短码查询只命中其中一条，另一条链接永久失效。唯一索引是最后一道防线 |
| `idx_z_surl_mapping_created_at` | `created_at` | 控制台列表按创建时间倒序翻页；也是清理过期短链的扫描入口 |

## 表名变更

| 项 | 值 |
|----|-----|
| 原名 | `z_ctc_surl_mapping`（从 z-ctc 抽出时沿用） |
| 新名 | `z_surl_mapping` |
| 为什么改 | 独立成仓后表名带 `ctc_` 前缀与仓名不符，且"短链归 4A 管"这个暗示是错的 |

**执行方：DBA / 运维。本仓不代跑 DDL，也不接生产库连接。**

执行顺序（详见迁移脚本）：

1. 查行数与表大小，确认窗口
2. 确认 `z_surl_mapping` 未被占用
3. `RENAME TABLE`（保留索引定义）
4. 补 `created_at` 索引
5. `SHOW CREATE TABLE` 核对

> **上线顺序约束**：本仓代码已全量切到新表名。**在生产库执行改名之前，新 jar 找不到旧表会 500。**
> 所以必须：① 停流量或先发兼容版 → ② 执行迁移 → ③ 部署新 jar。

## 同步校验

MySQL 权威 DDL 与 H2 dev 脚本是两份（MySQL 的 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`
子句 H2 的 MySQL 兼容模式不接受），**改列必须同步两处**。改完跑：

```bash
# 1) 列出两份 DDL 里的列名并 diff（应无输出）
grep -oiE '^\s+[a-z_]+\s+(BIGINT|VARCHAR|DATETIME|INT|TINYINT|TEXT|LONGTEXT)' \
    _doc/002_deploy/init/schema.sql | awk '{print $1}' | tr -d ' ' | sort > /tmp/ddl_mysql.txt
grep -oiE '^\s+[a-z_]+\s+(BIGINT|VARCHAR|DATETIME|INT|TINYINT|TEXT|LONGTEXT)' \
    z-surl-admin/src/main/resources/db/h2/schema.sql | awk '{print $1}' | tr -d ' ' | sort > /tmp/ddl_h2.txt
diff /tmp/ddl_mysql.txt /tmp/ddl_h2.txt && echo "OK: 0 diff"

# 2) DDL 列 vs 实体字段（应无输出）
grep -oE 'private [A-Za-z<>, ]+ [a-zA-Z]+;' z-surl-core/src/main/java/com/zifang/z/surl/domain/entity/SurlMappingDO.java \
    | awk '{print $NF}' | tr -d ';' | sed -E 's/([a-z0-9])([A-Z])/\1_\2/g' | tr 'A-Z' 'a-z' | sort > /tmp/do_fields.txt
diff /tmp/ddl_mysql.txt /tmp/do_fields.txt && echo "OK: DDL 与 DO 对齐"

# 3) 线上核对（连了生产库再跑）
mysql -h <host> -u<user> -p -e "SHOW CREATE TABLE z_surl_mapping\G"
```

> 仓库**没有**把这条校验做成自动闸门（`house-style-gate.sh` 只管测试代码约束）。
> 这是一个已知的缺口：列漂移要靠人记得跑上面第 1、2 步。补进闸门是 TODO。
