package com.zifang.z.tool.curl.service.impl;

import com.zifang.z.tool.curl.domain.entity.SurlMappingDO;
import com.zifang.z.tool.curl.domain.service.SurlDbService;
import com.zifang.z.tool.curl.service.SurlService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;

/**
 * MySQL 实现: 短码存 z_ctc_surl_mapping, 用 SecureRandom + Base62 生成 8 字符短码.
 * <p>
 * 冲突策略: 生成时 SELECT 检测, 冲突时重试 (最多 5 次), 仍冲突抛业务异常.
 * 业务方按需替换实现 (如 Redis incr 自增 / 雪花算法).
 */
@Service
public class CtcSurlServiceMySqlImpl implements SurlService {

    private static final Logger log = LogManager.getLogger(CtcSurlServiceMySqlImpl.class);
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int KEY_LENGTH = 8;
    private static final int MAX_RETRY = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SurlDbService surlDbService;

    public CtcSurlServiceMySqlImpl(SurlDbService surlDbService) {
        this.surlDbService = surlDbService;
    }

    @Override
    @Transactional
    public String shorten(String originUrl, String tenantCode, long ttlMillis, String createdBy, String remark) {
        if (originUrl == null || originUrl.isEmpty()) {
            throw new IllegalArgumentException("originUrl must not be empty");
        }
        String mapKey = generateUniqueKey();
        SurlMappingDO record = new SurlMappingDO();
        record.setMapKey(mapKey);
        record.setOriginUrl(originUrl);
        record.setTenantCode(tenantCode);
        record.setExpireAt(SurlService.computeExpireAt(ttlMillis));
        record.setHits(0L);
        record.setCreatedBy(createdBy);
        record.setRemark(remark);
        surlDbService.insert(record);
        log.info("CtcSurlServiceMySqlImpl.shorten: mapKey={}, tenant={}, ttlMillis={}, createdBy={}",
                mapKey, tenantCode, ttlMillis, createdBy);
        return mapKey;
    }

    @Override
    @Transactional
    public Optional<SurlMappingDO> resolve(String mapKey) {
        if (mapKey == null || mapKey.isEmpty()) {
            return Optional.empty();
        }
        SurlMappingDO record = surlDbService.selectByMapKey(mapKey);
        if (record == null) {
            return Optional.empty();
        }
        if (record.getExpireAt() != null && record.getExpireAt().isBefore(java.time.LocalDateTime.now())) {
            return Optional.empty();
        }
        surlDbService.incrementHits(mapKey);
        record.setHits(record.getHits() + 1);
        return Optional.of(record);
    }

    @Override
    public Optional<SurlMappingDO> info(String mapKey) {
        if (mapKey == null || mapKey.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(surlDbService.selectByMapKey(mapKey));
    }

    @Override
    @Transactional
    public boolean invalidate(String mapKey) {
        if (mapKey == null || mapKey.isEmpty()) {
            return false;
        }
        int affected = surlDbService.deleteByMapKey(mapKey);
        log.info("CtcSurlServiceMySqlImpl.invalidate: mapKey={}, affected={}", mapKey, affected);
        return affected > 0;
    }

    private String generateUniqueKey() {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            String key = randomKey();
            SurlMappingDO existing = surlDbService.selectByMapKey(key);
            if (existing == null) {
                return key;
            }
            log.debug("CtcSurlServiceMySqlImpl.generateUniqueKey 冲突重试: attempt={}, key={}", attempt, key);
        }
        throw new IllegalStateException("CtcSurlServiceMySqlImpl.generateUniqueKey 重试 " + MAX_RETRY + " 次仍冲突, 短码空间不足");
    }

    private String randomKey() {
        StringBuilder sb = new StringBuilder(KEY_LENGTH);
        for (int i = 0; i < KEY_LENGTH; i++) {
            sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return sb.toString();
    }
}
