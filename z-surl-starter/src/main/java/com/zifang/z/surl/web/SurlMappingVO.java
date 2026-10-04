package com.zifang.z.surl.web;

import com.zifang.z.surl.domain.entity.SurlMappingDO;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 短网址映射视图对象（管理面用）.
 */
public class SurlMappingVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String mapKey;
    private String originUrl;
    private String tenantCode;
    private LocalDateTime expireAt;
    private Long hits;
    private LocalDateTime createdAt;
    private String createdBy;
    private String remark;

    public static SurlMappingVO from(SurlMappingDO source) {
        if (source == null) {
            return null;
        }
        SurlMappingVO vo = new SurlMappingVO();
        vo.setId(source.getId());
        vo.setMapKey(source.getMapKey());
        vo.setOriginUrl(source.getOriginUrl());
        vo.setTenantCode(source.getTenantCode());
        vo.setExpireAt(source.getExpireAt());
        vo.setHits(source.getHits());
        vo.setCreatedAt(source.getCreatedAt());
        vo.setCreatedBy(source.getCreatedBy());
        vo.setRemark(source.getRemark());
        return vo;
    }

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
