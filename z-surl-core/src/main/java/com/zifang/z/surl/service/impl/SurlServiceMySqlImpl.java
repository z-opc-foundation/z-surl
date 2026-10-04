package com.zifang.z.surl.service.impl;

import com.zifang.z.surl.domain.entity.SurlMappingDO;
import com.zifang.z.surl.domain.service.SurlDbService;
import com.zifang.z.surl.service.SurlService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * MySQL 实现: 短码存 {@link SurlMappingDO#TABLE}, 用 SecureRandom + Base62 生成 8 字符短码.
 * <p>
 * 冲突策略: 生成时 SELECT 检测, 冲突时重试 (最多 5 次), 仍冲突抛业务异常。
 * 业务方按需替换实现 (如 Redis incr 自增 / 雪花算法)。
 * <p>
 * 为什么不用自增 ID 直接编码: 自增 ID 编码出来的短码会泄露业务量 (别人能反推你创建了多少条),
 * 且会暴露"某个 ID 存在/不存在"的信息。随机短码不泄露顺序。
 */
@Service
public class SurlServiceMySqlImpl implements SurlService {

    private static final Logger log = LogManager.getLogger(SurlServiceMySqlImpl.class);

    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int KEY_LENGTH = 8;
    private static final int MAX_RETRY = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SurlDbService surlDbService;

    public SurlServiceMySqlImpl(SurlDbService surlDbService) {
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
        record.setCreatedAt(LocalDateTime.now());
        record.setCreatedBy(createdBy);
        record.setRemark(remark);
        surlDbService.insert(record);
        log.info("SurlServiceMySqlImpl.shorten: mapKey={}, tenant={}, ttlMillis={}, createdBy={}",
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
        // 过期判定在 Java 侧做一次, 目的是"过期就完全不 +1"（下面 incrementHits 的 SQL 也带
        // expire_at > NOW() 条件, 两处判定都留着: SQL 侧防并发下的竞态, Java 侧避免给过期短链计数)
        if (record.getExpireAt() != null && record.getExpireAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        surlDbService.incrementHits(mapKey);
        // hits 可能为 null(老数据), null + 1 会 NPE
        long current = record.getHits() == null ? 0L : record.getHits();
        record.setHits(current + 1);
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
        log.info("SurlServiceMySqlImpl.invalidate: mapKey={}, affected={}", mapKey, affected);
        return affected > 0;
    }

    private String generateUniqueKey() {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            String key = randomKey();
            SurlMappingDO existing = surlDbService.selectByMapKey(key);
            if (existing == null) {
                return key;
            }
            log.debug("SurlServiceMySqlImpl.generateUniqueKey 冲突重试: attempt={}, key={}", attempt, key);
        }
        throw new IllegalStateException(
                "SurlServiceMySqlImpl.generateUniqueKey 重试 " + MAX_RETRY + " 次仍冲突, 短码空间不足");
    }

    private String randomKey() {
        StringBuilder sb = new StringBuilder(KEY_LENGTH);
        for (int i = 0; i < KEY_LENGTH; i++) {
            sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return sb.toString();
    }
}
