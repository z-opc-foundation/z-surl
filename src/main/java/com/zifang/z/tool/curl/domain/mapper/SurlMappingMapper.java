package com.zifang.z.tool.curl.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zifang.z.tool.curl.domain.entity.SurlMappingDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 短网址映射 Mapper.
 * <p>
 * 继承 MyBatis-Plus BaseMapper 获得 CRUD 基础能力; 业务方按需扩展.
 */
public interface SurlMappingMapper extends BaseMapper<SurlMappingDO> {

    /**
     * 命中 +1 (原子操作). 返回受影响行数, 0 表示 map_key 不存在.
     */
    @Update("UPDATE z_ctc_surl_mapping SET hits = hits + 1 WHERE map_key = #{mapKey} AND (expire_at IS NULL OR expire_at > NOW())")
    int incrementHits(@Param("mapKey") String mapKey);

    /**
     * 按短码查询 (无 expire_at 过滤, 业务方按需判断).
     */
    default SurlMappingDO selectByMapKey(String mapKey) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SurlMappingDO>()
                .eq("map_key", mapKey)
                .last("LIMIT 1"));
    }
}
