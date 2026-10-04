package com.zifang.z.surl.service;

import com.zifang.z.surl.domain.entity.SurlMappingDO;
import com.zifang.z.surl.service.impl.SurlServiceMySqlImpl;
import com.zifang.z.surl.support.InMemorySurlDb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SurlServiceMySqlImpl} 单测.
 * <p>
 * 约束（007 步骤 8）：纯 JUnit 5 + JDK 动态代理 + 内存 Map ——
 * <b>0 Mockito、0 DataSource/JdbcTemplate、0 {@code @SpringBootTest}</b>。
 * 所以这批测试不启动 Spring 容器、不连数据库，整类跑完在 1s 内。
 */
class SurlServiceMySqlImplTest {

    private static final String URL = "https://example.com/a/very/long/path?q=1";

    // ---------------------------------------------------------------- shorten

    @Test
    @DisplayName("shorten: 生成 8 字符短码并落库，字段完整")
    void shorten_persistsRecordAndReturnsEightCharKey() {
        InMemorySurlDb db = new InMemorySurlDb();
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        String mapKey = service.shorten(URL, "tenant-a", 0L, "alice", "备注X");

        assertNotNull(mapKey);
        assertEquals(8, mapKey.length(), "短码长度应为 8");
        assertEquals(1, db.store.size());
        SurlMappingDO saved = db.store.get(mapKey);
        assertEquals(URL, saved.getOriginUrl());
        assertEquals("tenant-a", saved.getTenantCode());
        assertEquals("alice", saved.getCreatedBy());
        assertEquals("备注X", saved.getRemark());
        assertEquals(0L, saved.getHits(), "新建记录命中数应为 0");
        assertNotNull(saved.getCreatedAt(), "创建时间必须由业务层落，不能依赖 DB default");
    }

    @Test
    @DisplayName("shorten: originUrl 为 null / 空串都拒绝，抛 IllegalArgumentException")
    void shorten_rejectsBlankOriginUrl() {
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(new InMemorySurlDb().dbService());

        assertThrows(IllegalArgumentException.class, () -> service.shorten(null, null, 0L, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.shorten("", null, 0L, null, null));
    }

    @Test
    @DisplayName("shorten: ttl > 0 换算成 expireAt")
    void shorten_computesExpireAtWhenTtlPositive() {
        InMemorySurlDb db = new InMemorySurlDb();
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        long ttl = 60_000L; // 1 分钟
        String mapKey = service.shorten(URL, null, ttl, null, null);

        LocalDateTime expireAt = db.store.get(mapKey).getExpireAt();
        assertNotNull(expireAt, "ttl>0 必须算出过期时间");
        long deltaMs = java.time.Duration.between(LocalDateTime.now(), expireAt).toMillis();
        assertTrue(Math.abs(deltaMs - ttl) < 5_000, "expireAt 应约等于 now+ttl，实际偏差 " + deltaMs + "ms");
    }

    @Test
    @DisplayName("shorten: ttl <= 0 表示永不过期，expireAt 落 null")
    void shorten_leavesExpireAtNullWhenTtlNonPositive() {
        InMemorySurlDb db = new InMemorySurlDb();
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        String zeroKey = service.shorten(URL, null, 0L, null, null);
        String negativeKey = service.shorten(URL, null, -1L, null, null);

        assertNull(db.store.get(zeroKey).getExpireAt(), "ttl=0 ⇒ 永不过期");
        assertNull(db.store.get(negativeKey).getExpireAt(), "ttl<0 ⇒ 永不过期");
    }

    // ---------------------------------------------------------------- resolve

    @Test
    @DisplayName("resolve: 命中并把 hits 回显成 +1")
    void resolve_returnsRecordAndIncrementsHits() {
        InMemorySurlDb db = new InMemorySurlDb().put(InMemorySurlDb.record("Ab12Cd34", URL, null));
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        Optional<SurlMappingDO> resolved = service.resolve("Ab12Cd34");

        assertTrue(resolved.isPresent());
        assertEquals(1L, resolved.get().getHits(), "返回值应已 +1");
        assertEquals(1L, db.store.get("Ab12Cd34").getHits(), "库里也应 +1");
        assertEquals(1, db.incrementCount.get());
    }

    @Test
    @DisplayName("resolve: 短码不存在返回 empty")
    void resolve_emptyWhenUnknown() {
        InMemorySurlDb db = new InMemorySurlDb();
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        assertFalse(service.resolve("NoSuchKey").isPresent());
    }

    @Test
    @DisplayName("resolve: mapKey 为 null / 空串返回 empty，且不查库")
    void resolve_emptyWhenBlankKey() {
        InMemorySurlDb db = new InMemorySurlDb();
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        assertFalse(service.resolve(null).isPresent());
        assertFalse(service.resolve("").isPresent());
        assertEquals(0, db.selectCount.get(), "空短码不该打到数据层");
    }

    @Test
    @DisplayName("resolve: 已过期返回 empty 且**不** +1（过期短链不该继续计数）")
    void resolve_emptyWhenExpiredAndDoesNotIncrement() {
        InMemorySurlDb db = new InMemorySurlDb().put(InMemorySurlDb.record(
                "Old00000", URL, LocalDateTime.now().minusMinutes(1)));
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        assertFalse(service.resolve("Old00000").isPresent());
        assertEquals(0, db.incrementCount.get(), "过期短链不该被 +1");
        assertEquals(0L, db.store.get("Old00000").getHits());
    }

    @Test
    @DisplayName("resolve: 库里 hits 为 null 时不 NPE，按 0 起算（老数据回归）")
    void resolve_handlesNullHitsWithoutNpe() {
        SurlMappingDO record = InMemorySurlDb.record("NullHit", URL, null);
        record.setHits(null);
        InMemorySurlDb db = new InMemorySurlDb().put(record);
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        Optional<SurlMappingDO> resolved = service.resolve("NullHit");

        assertTrue(resolved.isPresent());
        assertEquals(1L, resolved.get().getHits(), "null 应按 0 起算再 +1");
    }

    // ------------------------------------------------------------------- info

    @Test
    @DisplayName("info: 查元信息不增加命中")
    void info_returnsRecordWithoutIncrementingHits() {
        InMemorySurlDb db = new InMemorySurlDb().put(InMemorySurlDb.record("Info0001", URL, null));
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        Optional<SurlMappingDO> found = service.info("Info0001");

        assertTrue(found.isPresent());
        assertEquals(0L, found.get().getHits());
        assertEquals(0, db.incrementCount.get(), "info 是只读操作");
    }

    @Test
    @DisplayName("info: 不存在返回 empty；空短码返回 empty")
    void info_emptyWhenUnknownOrBlank() {
        InMemorySurlDb db = new InMemorySurlDb();
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        assertFalse(service.info("Missing").isPresent());
        assertFalse(service.info(null).isPresent());
        assertFalse(service.info("").isPresent());
    }

    // -------------------------------------------------------------- invalidate

    @Test
    @DisplayName("invalidate: 删掉记录并返回 true")
    void invalidate_deletesRecordAndReturnsTrue() {
        InMemorySurlDb db = new InMemorySurlDb().put(InMemorySurlDb.record("Del00001", URL, null));
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        assertTrue(service.invalidate("Del00001"));
        assertTrue(db.store.isEmpty());
        assertEquals(1, db.deleteCount.get());
    }

    @Test
    @DisplayName("invalidate: 记录不存在返回 false")
    void invalidate_falseWhenUnknown() {
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(new InMemorySurlDb().dbService());

        assertFalse(service.invalidate("Nope0000"));
    }

    @Test
    @DisplayName("invalidate: 空短码返回 false 且不打数据层")
    void invalidate_falseWhenBlankKey() {
        InMemorySurlDb db = new InMemorySurlDb();
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        assertFalse(service.invalidate(null));
        assertFalse(service.invalidate(""));
        assertEquals(0, db.deleteCount.get());
    }

    @Test
    @DisplayName("invalidate 之后再 resolve 立即 404（失效语义）")
    void invalidate_thenResolveIsGone() {
        InMemorySurlDb db = new InMemorySurlDb().put(InMemorySurlDb.record("Gone0001", URL, null));
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        assertTrue(service.resolve("Gone0001").isPresent());
        assertTrue(service.invalidate("Gone0001"));
        assertFalse(service.resolve("Gone0001").isPresent());
    }

    // ------------------------------------------------------- 短码生成冲突重试

    @Test
    @DisplayName("shorten: SELECT 撞到已占用的短码时自动重试，最终成功")
    void shorten_retriesOnConflict() {
        InMemorySurlDb db = new InMemorySurlDb().forceSelectConflicts(2); // 前两次都"已占用"
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        String mapKey = service.shorten(URL, null, 0L, null, null);

        assertNotNull(mapKey);
        assertTrue(db.store.containsKey(mapKey), "重试后应成功落库");
        assertTrue(db.selectCount.get() >= 3, "应至少探测 3 次（2 次冲突 + 1 次成功），实际 "
                + db.selectCount.get());
    }

    @Test
    @DisplayName("shorten: 连续冲突打满 5 次上限后抛 IllegalStateException")
    void shorten_throwsAfterMaxRetry() {
        InMemorySurlDb db = new InMemorySurlDb().forceAlwaysConflict();
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.shorten(URL, null, 0L, null, null));

        assertTrue(ex.getMessage().contains("5"), "报错应说明重试次数上限，实际: " + ex.getMessage());
        assertEquals(5, db.selectCount.get(), "MAX_RETRY=5 意味着最多探测 5 次");
    }

    @Test
    @DisplayName("短码字符集为 Base62，长度恒定 8，不含分隔符类字符")
    void shorten_keyShapeIsStable() {
        InMemorySurlDb db = new InMemorySurlDb();
        SurlServiceMySqlImpl service = new SurlServiceMySqlImpl(db.dbService());
        String base62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        for (int i = 0; i < 50; i++) {
            String key = service.shorten(URL, null, 0L, null, null);
            assertEquals(8, key.length());
            for (char c : key.toCharArray()) {
                assertTrue(base62.indexOf(c) >= 0, "短码含非 Base62 字符: " + c);
            }
        }
        assertEquals(50, db.store.size(), "50 次调用不应撞码（8 位 Base62 空间远大于 50）");
    }

    // --------------------------------------------------------- 静态工具方法

    @Test
    @DisplayName("computeExpireAt: ttl<=0 返回 null（永不过期）")
    void computeExpireAt_nullForNonPositive() {
        assertNull(SurlService.computeExpireAt(0L));
        assertNull(SurlService.computeExpireAt(-1L));
        assertNull(SurlService.computeExpireAt(Long.MIN_VALUE));
    }

    @Test
    @DisplayName("computeExpireAt: ttl>0 返回将来的时间点")
    void computeExpireAt_futureForPositive() {
        LocalDateTime expireAt = SurlService.computeExpireAt(3_600_000L); // 1 小时
        assertNotNull(expireAt);
        assertTrue(expireAt.isAfter(LocalDateTime.now()), "应是将来的时间点");
        assertTrue(expireAt.isBefore(LocalDateTime.now().plusHours(2)), "不应超过 2 小时后");
    }
}
