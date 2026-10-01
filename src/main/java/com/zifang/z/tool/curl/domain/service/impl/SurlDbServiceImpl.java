package com.zifang.z.tool.curl.domain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zifang.z.tool.curl.domain.entity.SurlMappingDO;
import com.zifang.z.tool.curl.domain.mapper.SurlMappingMapper;
import com.zifang.z.tool.curl.domain.service.SurlDbService;
import org.springframework.stereotype.Service;

@Service
public class SurlDbServiceImpl implements SurlDbService {

    private final SurlMappingMapper mapper;

    public SurlDbServiceImpl(SurlMappingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(SurlMappingDO entity) {
        return mapper.insert(entity);
    }

    @Override
    public int incrementHits(String mapKey) {
        return mapper.incrementHits(mapKey);
    }

    @Override
    public SurlMappingDO selectByMapKey(String mapKey) {
        return mapper.selectByMapKey(mapKey);
    }

    @Override
    public int deleteByMapKey(String mapKey) {
        return mapper.delete(new QueryWrapper<SurlMappingDO>().eq("map_key", mapKey));
    }
}
