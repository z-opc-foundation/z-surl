-- ============================================================================
-- z-surl 权威建表脚本（唯一 DDL 源）
--
-- 本文件是 z-surl 的**唯一**表结构权威源。仓内不再出现第二份 db.sql / migration.sql
-- 的整建副本（007 步骤 7 踩坑 #14：两处互相覆盖、字段漂移）。
-- 增量变更写在 migration/ 下，一次一个文件、只做 ALTER/RENAME，不重复整建。
--
-- 表结构与 SurlMappingDO 字段 1:1 对齐，校验命令见
--   _doc/001_arch/03-db-schema.md §同步校验
--
-- 目标库：组织主库（db oc）。库名由部署方 z.base.db.surl.database 决定，
-- 本脚本**不建库**（CREATE DATABASE 由 DBA 单独执行，避免脚本隐式改库配置）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS z_surl_mapping
(
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    map_key     VARCHAR(16)   NOT NULL COMMENT '短码 Base62 8 字符，业务唯一键',
    origin_url  VARCHAR(2048) NOT NULL COMMENT '原长链接',
    tenant_code VARCHAR(64)   DEFAULT NULL COMMENT '归属租户，NULL=公共链接',
    expire_at   DATETIME      DEFAULT NULL COMMENT '过期时间，NULL=永不过期',
    hits        BIGINT        NOT NULL DEFAULT 0 COMMENT '跳转命中次数',
    created_at  DATETIME      NOT NULL COMMENT '创建时间',
    created_by  VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    remark      VARCHAR(512)  DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    -- map_key 走唯一索引而不是普通索引：短码生成靠"SELECT 不存在才插入"避让，
    -- 并发下两个请求可能拿到同一短码，唯一索引是最后一道防线 ——
    -- 没有它重复短码会静默入库，之后按短码查询只命中其中一条，另一条链接永久失效。
    UNIQUE KEY uk_z_surl_mapping_map_key (map_key),
    -- 控制台列表按"创建时间倒序"翻页用；也是数据量增长后清理过期短链的扫描入口
    KEY idx_z_surl_mapping_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT ='z-surl 短网址映射表';
