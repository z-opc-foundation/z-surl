package com.zifang.z.surl.domain.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zifang.z.surl.domain.entity.SurlMappingDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 短网址映射 Mapper.
 * <p>
 * 继承 MyBatis-Plus BaseMapper 获得 CRUD 基础能力; 业务方按需扩展.
 */
public interface SurlMappingMapper extends BaseMapper<SurlMappingDO> {

    /**
     * 命中 +1 (原子操作). 返回受影响行数, 0 表示 map_key 不存在或已过期.
     * <p>
     * 表名走 {@link SurlMappingDO#TABLE} 常量而不是字面量: 改表名只改一处。
     * <p>
     * 过期判定用 {@code CURRENT_TIMESTAMP} 而不是在 Java 侧比时间: 多副本部署时各机器时钟有偏差,
     * 判定必须以数据库的时钟为准, 否则会出现"DB 认为没过期、某台应用机认为过期了"的漂移。
     * <p>
     * 写 {@code CURRENT_TIMESTAMP} 而不是 {@code NOW()}: 两者在 MySQL 8 等价, 但
     * {@code NOW()} 是 MySQL 方言, admin 的 dev profile 走 H2 时会直接报语法错
     * (H2 只有 {@code CURRENT_TIMESTAMP})。
     */
    @Update("UPDATE " + SurlMappingDO.TABLE
            + " SET hits = hits + 1"
            + " WHERE map_key = #{mapKey}"
            + " AND (expire_at IS NULL OR expire_at > CURRENT_TIMESTAMP)")
    int incrementHits(@Param("mapKey") String mapKey);

    /**
     * 按短码查询 (无 expire_at 过滤, 业务方按需判断).
     */
    default SurlMappingDO selectByMapKey(String mapKey) {
        return selectOne(new QueryWrapper<SurlMappingDO>()
                .eq("map_key", mapKey)
                .last("LIMIT 1"));
    }
}
