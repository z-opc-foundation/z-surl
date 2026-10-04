package com.zifang.z.surl.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * dev profile 数据源: H2 内存库, 零外部依赖一键启动.
 * <p>
 * <b>为什么必须覆盖 bean, 而不能只写配置</b>:
 * {@code z-boot-datasource-starter} 的 {@code ModuleDataSourceTemplate#buildDataSource}
 * 是**写死 Druid + MySQL 驱动**的, 它用 {@code z.base.db.surl.{host,port,database,...}}
 * 拼出 {@code jdbc:mysql://} URL —— 没有 {@code url} 配置项可覆盖, 换不了 H2。
 * 所以 dev 下要拿到 H2, 只能自己声明同名 {@code dataSourceSurl} bean:
 * starter 里的 {@code @Bean("dataSourceSurl") + @ConditionalOnMissingBean(name="dataSourceSurl")}
 * 在自动装配阶段看到本 bean 已存在会**主动退让**, 而
 * {@code sqlSessionFactorySurl} 是按名注入 {@code dataSourceSurl}, 会自动接到 H2 上。
 * <p>
 * 生效方式: {@code --spring.profiles.active=dev}。
 * 生产/联调请用默认 profile 走真实 MySQL, 别拿内存库当依赖（重启即丢数据）。
 */
@Configuration
@Profile("dev")
public class DevDataSourceConfig {

    /**
     * H2 内存库, 建表脚本 {@code db/h2/schema.sql}.
     * <p>
     * <b>name 里只能写 MODE，写 DB_CLOSE_DELAY / DB_CLOSE_ON_EXIT 会启动直接失败。</b>
     * Spring 的 {@code H2EmbeddedDatabaseConfigurer} 把 URL 模板写死成
     * {@code jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false}，name 是被
     * {@code String.format} 填进 %s 的。所以你在这两个属性上再写一遍，最终 URL 里
     * 就出现两次，H2 直接报：
     * <pre>
     *   org.h2.jdbc.JdbcSQLNonTransientConnectionException:
     *       Duplicate property "DB_CLOSE_ON_EXIT" [90066-214]
     * </pre>
     * 连带把整个数据源初始化打断，表现是启动期
     * {@code Failed to obtain JDBC Connection}，而不是一条清晰的配置报错。
     * 排掉这两个属性后 builder 自己会补上，语义不变（内存库不被关闭）。
     * <p>
     * 脚本与 {@code _doc/002_deploy/init/schema.sql}（MySQL 权威 DDL）是两份, 改列必须同步两处
     * —— 校验命令见 {@code _doc/001_arch/03-db-schema.md}。
     */
    @Bean("dataSourceSurl")
    public DataSource devDataSourceSurl() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("surl;MODE=MySQL")
                .addScript("classpath:db/h2/schema.sql")
                .build();
    }
}
