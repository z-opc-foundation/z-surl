package com.zifang.z.surl.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 短网址映射实体.
 * <p>
 * 字段命名与 {@code _doc/002_deploy/init/schema.sql} 一一对应
 * (snake_case → Java camelCase 由 MyBatis-Plus 自动映射)。
 * <p>
 * 表名 {@value #TABLE}：原名 {@code z_ctc_surl_mapping}（从 z-ctc 抽出时沿用），
 * 本仓独立后换名为 {@code z_surl_mapping}。线上库的改名脚本见
 * {@code _doc/002_deploy/init/migration/001_rename_z_ctc_surl_mapping.sql}，
 * **由 DBA 择机执行**，本仓不代跑 DDL。
 * <p>
 * 手写 getter/setter：组织规范强制 No Lombok（JDK 25 兼容性）。
 */
@TableName(SurlMappingDO.TABLE)
public class SurlMappingDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 物理表名。DO / Mapper / schema.sql 三处必须同源，改名只改这一格。
     */
    public static final String TABLE = "z_surl_mapping";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 短码 (Base62, 8 字符). 业务唯一键.
     */
    @TableField("map_key")
    private String mapKey;

    /**
     * 原长链接.
     */
    @TableField("origin_url")
    private String originUrl;

    /**
     * 归属租户 (可空 = 公共链接).
     */
    @TableField("tenant_code")
    private String tenantCode;

    /**
     * 过期时间 (可空 = 永不过期).
     */
    @TableField("expire_at")
    private LocalDateTime expireAt;

    /**
     * 跳转命中次数.
     */
    @TableField("hits")
    private Long hits;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("remark")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMapKey() {
        return mapKey;
    }

    public void setMapKey(String mapKey) {
        this.mapKey = mapKey;
    }

    public String getOriginUrl() {
        return originUrl;
    }

    public void setOriginUrl(String originUrl) {
        this.originUrl = originUrl;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public Long getHits() {
        return hits;
    }

    public void setHits(Long hits) {
        this.hits = hits;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
