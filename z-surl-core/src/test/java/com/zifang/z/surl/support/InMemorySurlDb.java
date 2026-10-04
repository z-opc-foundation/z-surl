package com.zifang.z.surl.support;

import com.zifang.z.surl.domain.entity.SurlMappingDO;
import com.zifang.z.surl.domain.service.SurlDbService;

import java.lang.reflect.InvocationHandler;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试用内存版数据层：{@code Map} 充当 DB + JDK 动态代理充当 {@link SurlDbService}.
 * <p>
 * 行为刻意贴近真库两处，否则单测会给出假绿：
 * <ol>
 *   <li><b>insert 存的是快照副本</b>：真 DB 存的是行快照，Java 侧后续改 POJO 不会回写。
 *       直接存同一个对象引用的话，测试会"看到"本该只在内存里的改动。</li>
 *   <li><b>selectByMapKey 返回副本</b>：调用方改返回值不该影响库里数据。
 *       （{@code resolve} 就是靠改返回值把 hits 回显给调用方的。）</li>
 * </ol>
 * 调用次数用 {@link #selectCount} / {@link #incrementCount} / {@link #deleteCount} 暴露，
 * 用来断言"过期短链不该被 +1"、"冲突要重试 N 次"这类**交互**约束 ——
 * 只断言返回值断言不到这些。
 */
public final class InMemorySurlDb {

    /** map_key → 记录（存的是副本） */
    public final Map<String, SurlMappingDO> store = new ConcurrentHashMap<>();

    public final AtomicInteger selectCount = new AtomicInteger();
    public final AtomicInteger incrementCount = new AtomicInteger();
    public final AtomicInteger deleteCount = new AtomicInteger();

    private final AtomicLong idSeq = new AtomicLong(0);

    /**
     * 非空时，<b>前 {@code n} 次</b> selectByMapKey 直接返回 {@link #probeSentinel}，
     * 用来模拟"生成短码时 SELECT 发现已占用 ⇒ 冲突重试"。
     */
    private volatile int forcedSelectConflicts = 0;
    private volatile boolean alwaysConflict = false;

    /**
     * selectByMapKey 强制返回的哨兵对象（只用于让调用方判定"非 null ⇒ 冲突"）。
     */
    private static final SurlMappingDO PROBE_SENTINEL = new SurlMappingDO();

    private final SurlDbService dbService;

    public InMemorySurlDb() {
        Map<String, InvocationHandler> handlers = new HashMap<>();
        handlers.put("insert", (p, m, args) -> {
            SurlMappingDO entity = (SurlMappingDO) args[0];
            if (entity.getId() == null) {
                entity.setId(idSeq.incrementAndGet());
            }
            store.put(entity.getMapKey(), copyOf(entity));
            return 1;
        });
        handlers.put("selectByMapKey", (p, m, args) -> {
            selectCount.incrementAndGet();
            if (alwaysConflict) {
                return PROBE_SENTINEL;
            }
            if (forcedSelectConflicts > 0) {
                forcedSelectConflicts--;
                return PROBE_SENTINEL;
            }
            SurlMappingDO found = store.get((String) args[0]);
            return found == null ? null : copyOf(found);
        });
        handlers.put("incrementHits", (p, m, args) -> {
            incrementCount.incrementAndGet();
            SurlMappingDO found = store.get((String) args[0]);
            if (found == null) {
                return 0;
            }
            long current = found.getHits() == null ? 0L : found.getHits();
            found.setHits(current + 1);
            return 1;
        });
        handlers.put("deleteByMapKey", (p, m, args) -> {
            deleteCount.incrementAndGet();
            return store.remove((String) args[0]) == null ? 0 : 1;
        });
        this.dbService = Proxies.of(SurlDbService.class, handlers);
    }

    public SurlDbService dbService() {
        return dbService;
    }

    /** 让前 n 次 selectByMapKey 返回"已占用"，模拟短码冲突。 */
    public InMemorySurlDb forceSelectConflicts(int n) {
        this.forcedSelectConflicts = n;
        return this;
    }

    /** 让所有 selectByMapKey 都返回"已占用"，用来打满重试上限。 */
    public InMemorySurlDb forceAlwaysConflict() {
        this.alwaysConflict = true;
        return this;
    }

    /** 预置一条记录。 */
    public InMemorySurlDb put(SurlMappingDO record) {
        store.put(record.getMapKey(), copyOf(record));
        return this;
    }

    /** 预置一条最简记录。 */
    public static SurlMappingDO record(String mapKey, String originUrl, LocalDateTime expireAt) {
        SurlMappingDO d = new SurlMappingDO();
        d.setMapKey(mapKey);
        d.setOriginUrl(originUrl);
        d.setExpireAt(expireAt);
        d.setHits(0L);
        d.setCreatedAt(LocalDateTime.now());
        return d;
    }

    private static SurlMappingDO copyOf(SurlMappingDO src) {
        SurlMappingDO d = new SurlMappingDO();
        d.setId(src.getId());
        d.setMapKey(src.getMapKey());
        d.setOriginUrl(src.getOriginUrl());
        d.setTenantCode(src.getTenantCode());
        d.setExpireAt(src.getExpireAt());
        d.setHits(src.getHits());
        d.setCreatedAt(src.getCreatedAt());
        d.setCreatedBy(src.getCreatedBy());
        d.setRemark(src.getRemark());
        return d;
    }
}
