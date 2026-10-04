package com.zifang.z.surl.service;

import com.zifang.z.surl.domain.entity.SurlMappingDO;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 短网址服务接口 —— 业务方 import 的领域能力.
 * <p>
 * 设计哲学: 短链场景核心能力 = "生码 + 解析 + 命中统计 + 失效", 抽象为 4 个方法.
 * 业务方按需注入, 不依赖具体实现 (in-memory / MySQL / Redis 自由替换)。
 * <p>
 * 返回短码而不是完整 URL: 前缀 (域名 + 路径) 由部署方决定, 库不该把它固化。
 */
public interface SurlService {

    /**
     * 工具: 计算 expireAt (可空 = 永不过期).
     *
     * @param ttlMillis 有效期毫秒, &lt;= 0 表示永不过期
     */
    static LocalDateTime computeExpireAt(long ttlMillis) {
        if (ttlMillis <= 0) {
            return null;
        }
        return LocalDateTime.now().plusNanos(ttlMillis * 1_000_000L);
    }

    /**
     * 生成短码.
     *
     * @param originUrl  原 URL (必填, 业务方应自行校验 URL 合法性)
     * @param tenantCode 租户 (可空, 公共链接传 null)
     * @param ttlMillis  有效期毫秒 (0 或负数 = 永不过期)
     * @param createdBy  创建人 (可空)
     * @param remark     备注 (可空)
     * @return 短码 (8 字符 Base62). 业务方自行拼前缀得到完整短链 URL.
     */
    String shorten(String originUrl, String tenantCode, long ttlMillis, String createdBy, String remark);

    /**
     * 解析短码 → 原 URL. 命中 +1 (原子). 过期/不存在返回 empty.
     */
    Optional<SurlMappingDO> resolve(String mapKey);

    /**
     * 查询元信息 (不增加命中).
     */
    Optional<SurlMappingDO> info(String mapKey);

    /**
     * 主动失效某个短码. 返回是否成功.
     * <p>
     * 语义说明: 本实现是**物理删除**（DELETE）。短链场景下已发出的引用无法回收,
     * 所以"失效"只保证后续解析立即 404, 不保证第三方已缓存的页面失效。
     */
    boolean invalidate(String mapKey);
}
