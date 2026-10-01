package com.zifang.z.tool.curl.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zifang.z.boot.datasource.starter.ModuleDataSourceTemplate;
import org.apache.ibatis.plugin.Interceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * z-tool-curl 自动装配 (FEATURE054: 短链服务独立集成到 z-opc).
 * <p>
 * 通过 {@code META-INF/spring.factories} 注册, Spring Boot 启动时自动加载.
 * <h3>独立性</h3>
 * z-tool-curl 自带独立 {@code dataSourceCurl} + {@code sqlSessionFactoryCurl}, 不复用任何其他模块.
 * 配置前缀: {@code z.base.db.curl.*} (与 z-ctc 用的 {@code z.base.db.ctc.*} 平行).
 * <h3>职责</h3>
 * <ul>
 *   <li>{@code dataSourceCurl}: z-tool-curl 独立数据源 (Druid 连接池)</li>
 *   <li>{@code sqlSessionFactoryCurl}: 独立 SqlSessionFactory (MyBatis-Plus + 分页拦截器)</li>
 *   <li>{@code @MapperScan(sqlSessionFactoryRef = "sqlSessionFactoryCurl")}: 短链 mapper 走独立工厂</li>
 *   <li>{@code @ComponentScan}: SurlController + SurlService 注入</li>
 * </ul>
 *
 * @author zifang
 */
@AutoConfiguration
@Configuration
@ComponentScan(basePackages = "com.zifang.z.tool.curl")
@MapperScan(
        basePackages = "com.zifang.z.tool.curl.domain.mapper",
        sqlSessionFactoryRef = "sqlSessionFactoryCurl",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
public class ZToolCurlAutoConfiguration extends ModuleDataSourceTemplate {

    /**
     * z-tool-curl 独立数据源 (与 z-ctc 平行, 配置前缀 z.base.db.curl.*).
     * <p>
     * 物理上: 默认沿用 z.base.db.default (与 z-ctc 同 MySQL 实例, 同 database oc),
     * 但 Java 侧是独立的 DataSource bean, 短链表 z_surl_mapping 的元数据不归 z-ctc 管辖.
     */
    @Bean("dataSourceCurl")
    @ConditionalOnMissingBean(name = "dataSourceCurl")
    public DataSource dataSourceCurl(Environment env) {
        return buildDataSource(env, "curl");
    }

    /**
     * MyBatis-Plus 分页拦截器 (curl 独立).
     * <p>
     * 没有 {@link PaginationInnerInterceptor} 时, {@code BaseMapper.selectPage()} 会抛
     * {@code BadSqlGrammarException}. 短链 SurlDbServiceImpl 用的是简单 selectByMapKey
     * (不调 selectPage), 但保留分页插件以防未来分页查询.
     */
    @Bean("mybatisPlusInterceptorCurl")
    @ConditionalOnMissingBean(name = "mybatisPlusInterceptorCurl")
    public MybatisPlusInterceptor mybatisPlusInterceptorCurl() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    /**
     * z-tool-curl 独立 SqlSessionFactory.
     */
    @Bean("sqlSessionFactoryCurl")
    @ConditionalOnMissingBean(name = "sqlSessionFactoryCurl")
    public MybatisSqlSessionFactoryBean sqlSessionFactoryCurl(
            DataSource dataSourceCurl,
            MybatisPlusInterceptor mybatisPlusInterceptorCurl) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSourceCurl);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:/mapper/**/*.xml"));
        factoryBean.setTypeAliasesPackage("com.zifang.z.tool.curl.domain.entity");
        factoryBean.setPlugins(new Interceptor[]{mybatisPlusInterceptorCurl});
        return factoryBean;
    }
}
