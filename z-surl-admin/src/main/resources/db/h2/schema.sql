-- ============================================================================
-- z-surl dev profile（H2 内存库）建表脚本
--
-- 权威 DDL 在  _doc/002_deploy/init/schema.sql（MySQL 8，面向真实库）。
-- 这份是它的 H2 等价物, 供 `--spring.profiles.active=dev` 一键启动（不依赖 MySQL）。
--
-- 两份**必须同步改列**：本仓没有自动化去校验它们一致，列漂移的表现是
-- dev 跑得通、上了 MySQL 才炸（或反过来）。同步校验命令见
--   _doc/001_arch/03-db-schema.md §同步校验
--
-- 为什么不共用同一份文件: MySQL 的 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`
-- 子句 H2 的 MySQL 兼容模式不接受。
-- ============================================================================

CREATE TABLE IF NOT EXISTS z_surl_mapping
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    map_key     VARCHAR(16)  NOT NULL COMMENT '短码 Base62 8 字符, 业务唯一键',
    origin_url  VARCHAR(2048) NOT NULL COMMENT '原长链接',
    tenant_code VARCHAR(64)           DEFAULT NULL COMMENT '归属租户, NULL=公共链接',
    expire_at   DATETIME              DEFAULT NULL COMMENT '过期时间, NULL=永不过期',
    hits        BIGINT       NOT NULL DEFAULT 0 COMMENT '跳转命中次数',
    created_at  DATETIME     NOT NULL COMMENT '创建时间',
    created_by  VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    remark      VARCHAR(512)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id)
);

-- map_key 走唯一索引而不是普通索引: 短码生成靠"SELECT 不存在才插入"避让, 并发下两个请求
-- 拿到同一短码时, 唯一索引是最后一道防线 —— 没有它, 重复短码会静默写进去,
-- 之后按短码查询只会命中其中一条, 另一条链接永久失效。
-- dev 库用 H2, 语法与 MySQL 一致。
CREATE UNIQUE INDEX uk_z_surl_mapping_map_key ON z_surl_mapping (map_key);
