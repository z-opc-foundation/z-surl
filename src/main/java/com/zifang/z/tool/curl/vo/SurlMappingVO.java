package com.zifang.z.tool.curl.vo;

import com.zifang.z.tool.curl.domain.entity.SurlMappingDO;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 短网址映射视图对象.
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

    public static SurlMappingVO from(SurlMappingDO do_) {
        if (do_ == null) {
            return null;
        }
        SurlMappingVO vo = new SurlMappingVO();
        vo.setId(do_.getId());
        vo.setMapKey(do_.getMapKey());
        vo.setOriginUrl(do_.getOriginUrl());
        vo.setTenantCode(do_.getTenantCode());
        vo.setExpireAt(do_.getExpireAt());
        vo.setHits(do_.getHits());
        vo.setCreatedAt(do_.getCreatedAt());
        vo.setCreatedBy(do_.getCreatedBy());
        vo.setRemark(do_.getRemark());
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