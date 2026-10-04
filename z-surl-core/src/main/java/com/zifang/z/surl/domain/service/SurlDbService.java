package com.zifang.z.surl.domain.service;

import com.zifang.z.surl.domain.entity.SurlMappingDO;

/**
 * 短网址 DbService —— 数据访问层.
 * <p>
 * 单独抽一层是为了让 {@code SurlServiceImpl} 的单测能用 JDK 动态代理把这一层整体替换掉
 * （0 Mockito / 0 DB，见 007 步骤 8）。业务方换成 Redis / in-memory 时只换这一层的实现。
 */
public interface SurlDbService {

    int insert(SurlMappingDO entity);

    int incrementHits(String mapKey);

    SurlMappingDO selectByMapKey(String mapKey);

    int deleteByMapKey(String mapKey);
}
