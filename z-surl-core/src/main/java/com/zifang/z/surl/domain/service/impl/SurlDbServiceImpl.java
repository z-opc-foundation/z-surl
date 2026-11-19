package com.zifang.z.surl.domain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zifang.z.meta.api.annotation.BizMethod;
import com.zifang.z.surl.domain.entity.SurlMappingDO;
import com.zifang.z.surl.domain.mapper.SurlMappingMapper;
import com.zifang.z.surl.domain.service.SurlDbService;
import org.springframework.stereotype.Service;

/**
 * MyBatis-Plus 实现: 走 {@link SurlMappingMapper}.
 */
@Service
public class SurlDbServiceImpl implements SurlDbService {

    private final SurlMappingMapper mapper;

    public SurlDbServiceImpl(SurlMappingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @BizMethod(name = "insert", module = "z-surl", description = "", operation = BizMethod.Operation.CREATE)
    public int insert(SurlMappingDO entity) {
        return mapper.insert(entity);
    }

    @Override
    @BizMethod(name = "incrementHits", module = "z-surl", description = "", operation = BizMethod.Operation.ACTION)
    public int incrementHits(String mapKey) {
        return mapper.incrementHits(mapKey);
    }

    @Override
    @BizMethod(name = "selectByMapKey", module = "z-surl", description = "", operation = BizMethod.Operation.QUERY)
    public SurlMappingDO selectByMapKey(String mapKey) {
        return mapper.selectByMapKey(mapKey);
    }

    @Override
    @BizMethod(name = "deleteByMapKey", module = "z-surl", description = "", operation = BizMethod.Operation.DELETE)
    public int deleteByMapKey(String mapKey) {
        return mapper.delete(new QueryWrapper<SurlMappingDO>().eq("map_key", mapKey));
    }
}



