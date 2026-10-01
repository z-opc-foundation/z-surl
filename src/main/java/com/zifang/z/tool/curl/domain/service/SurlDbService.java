package com.zifang.z.tool.curl.domain.service;

import com.zifang.z.tool.curl.domain.entity.SurlMappingDO;

/**
 * 短网址 DbService.
 */
public interface SurlDbService {

    int insert(SurlMappingDO entity);

    int incrementHits(String mapKey);

    SurlMappingDO selectByMapKey(String mapKey);

    int deleteByMapKey(String mapKey);
}
