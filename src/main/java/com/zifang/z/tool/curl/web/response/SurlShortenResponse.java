package com.zifang.z.tool.curl.web.response;

import java.time.LocalDateTime;

/**
 * 生成短码响应.
 */
public class SurlShortenResponse {

    private String mapKey;
    private String originUrl;
    private LocalDateTime expireAt;
    private Long hits;

    public SurlShortenResponse() {
    }

    public SurlShortenResponse(String mapKey, String originUrl, LocalDateTime expireAt, Long hits) {
        this.mapKey = mapKey;
        this.originUrl = originUrl;
        this.expireAt = expireAt;
        this.hits = hits;
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
}
