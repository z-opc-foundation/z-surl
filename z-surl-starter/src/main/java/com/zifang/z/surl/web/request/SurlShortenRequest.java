package com.zifang.z.surl.web.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 生成短码请求.
 * <p>
 * 注意: Controller 里对 {@code originUrl} 还做了一次显式判空并返回 400, 而不是只靠
 * {@code @Valid} —— 因为 body 本身可空（{@code required = false}），触发不了校验，
 * 缺 body 会一路走到业务层变成 500。见 {@code SurlController#shorten}。
 */
public class SurlShortenRequest {

    @NotBlank(message = "originUrl must not be blank")
    @Size(max = 2048, message = "originUrl 长度不超过 2048")
    private String originUrl;

    /**
     * 租户 (可空 = 公共链接).
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
