package com.zifang.z.surl.autoconfigure;

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
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * z-surl 自动装配.
 * <p>
 * 通过 {@code META-INF/spring.factories} 注册, Spring Boot 启动时自动加载, 业务方一行依赖即用。
 * <h3>独立性</h3>
 * z-surl 自带独立 {@code dataSourceSurl} + {@code sqlSessionFactorySurl}, 不复用其他模块的
 * 数据源或 MyBatis 工厂。配置前缀 {@code z.base.db.surl.*}（与 z-ctc 的 {@code z.base.db.ctc.*}
 * 平行但完全独立）。
 * <h3>职责</h3>
 * <ul>
 *   <li>{@code dataSourceSurl}: z-surl 独立数据源（连接池由 z-boot-datasource-starter 提供）</li>
 *   <li>{@code sqlSessionFactorySurl}: 独立 SqlSessionFactory（MyBatis-Plus + 分页拦截器）</li>
 *   <li>{@code @MapperScan(sqlSessionFactoryRef = "sqlSessionFactorySurl")}: 短链 mapper 走独立工厂</li>
 *   <li>{@code @ComponentScan}: SurlController + 两个 Service 实现</li>
 * </ul>
 *
 * @author yuku123
 */
@AutoConfiguration
@ComponentScan(
        /*
         * 显式列三个包, 不写 "com.zifang.z.surl":
         * z-surl-admin 的 @SpringBootApplication 就在 com.zifang.z.surl.admin 下, 宽包扫描会把它
         * 也注册成 bean（@SpringBootApplication 隐含 @Configuration），在业务方应用里造成
         * "一个启动类被注册两次" 的 NoUniqueBeanDefinitionException。列包即免疫。
         */
        basePackages = {
                "com.zifang.z.surl.web",
                "com.zifang.z.surl.service.impl",
                "com.zifang.z.surl.domain.service.impl"
        }
)
@MapperScan(
        basePackages = "com.zifang.z.surl.domain.mapper",
        sqlSessionFactoryRef = "sqlSessionFactorySurl",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
public class ZSurlAutoConfiguration extends ModuleDataSourceTemplate {

    /**
     * z-surl 独立数据源.
     * <p>
     * 物理上: 默认沿用 z.base.db.default（与组织其他模块同 MySQL 实例、同 database），
     * 但 Java 侧是独立的 DataSource bean —— 短链表 {@code z_surl_mapping} 的元数据不归任何其他模块管辖。
     */
    @Bean("dataSourceSurl")
    @ConditionalOnMissingBean(name = "dataSourceSurl")
    public DataSource dataSourceSurl(Environment env) {
        return buildDataSource(env, "surl");
    }

    /**
     * MyBatis-Plus 分页拦截器（surl 独立）.
     * <p>
     * 没有 {@link PaginationInnerInterceptor} 时 {@code BaseMapper.selectPage()} 会抛
     * {@code BadSqlGrammarException}。当前 SurlDbServiceImpl 用的是简单 selectByMapKey
     * （不调 selectPage），但保留分页插件以备控制台将来做分页列表。
     */
    @Bean("mybatisPlusInterceptorSurl")
    @ConditionalOnMissingBean(name = "mybatisPlusInterceptorSurl")
    public MybatisPlusInterceptor mybatisPlusInterceptorSurl() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    /**
     * z-surl 独立 SqlSessionFactory.
     */
    @Bean("sqlSessionFactorySurl")
    @ConditionalOnMissingBean(name = "sqlSessionFactorySurl")
    public MybatisSqlSessionFactoryBean sqlSessionFactorySurl(
            DataSource dataSourceSurl,
            MybatisPlusInterceptor mybatisPlusInterceptorSurl) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSourceSurl);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath*:/mapper/**/*.xml"));
        factoryBean.setTypeAliasesPackage("com.zifang.z.surl.domain.entity");
        factoryBean.setPlugins(new Interceptor[]{mybatisPlusInterceptorSurl});
        return factoryBean;
    }
}
