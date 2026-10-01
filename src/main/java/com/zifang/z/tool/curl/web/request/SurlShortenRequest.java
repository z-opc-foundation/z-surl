package com.zifang.z.tool.curl.web.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 生成短码请求.
 */
public class SurlShortenRequest {

    @NotBlank(message = "originUrl must not be blank")
    @Size(max = 2048, message = "originUrl 长度不超过 2048")
    private String originUrl;

    /**
     * 租户 (可空).
     */
    private String tenantCode;

    /**
     * 有效期毫秒. 0 或负数 = 永不过期.
     */
    private Long ttlMillis;

    /**
     * 备注 (可空).
     */
    private String remark;

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

    public Long getTtlMillis() {
        return ttlMillis;
    }

    public void setTtlMillis(Long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
