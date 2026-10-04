package com.zifang.z.surl.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * z-surl admin 启动类 —— 独立可启动单体 (java -jar), 同时是 Docker / k8s 镜像源.
 * <p>
 * <b>刻意写成 bare {@code @SpringBootApplication}</b>: 不带 {@code scanBasePackages}。
 * <ul>
 *   <li>本类只扫到 {@code com.zifang.z.surl.admin}（空壳，就它自己）</li>
 *   <li>业务 bean（Controller / 两个 Service）由 z-surl-starter 的
 *       {@code ZSurlAutoConfiguration} 里的 {@code @ComponentScan} 显式列包注册</li>
 * </ul>
 * 若这里写 {@code scanBasePackages = "com.zifang.z.surl"}, 会与 starter 的
 * {@code @ComponentScan} 叠加, 造成同一 bean 注册两次的
 * {@code NoUniqueBeanDefinitionException}（007 踩坑：NoUniqueBeanDefinitionException）。
 * <p>
 * 启动：
 * <pre>
 *   dev  profile（H2 内存库，不依赖 MySQL）：java -jar z-surl-admin-*-exec.jar --spring.profiles.active=dev
 *   prod（走 z.base.db.surl.* 连真实 MySQL）  ：java -jar z-surl-admin-*-exec.jar
 * </pre>
 *
 * @author yuku123
 */
@SpringBootApplication
public class SurlAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(SurlAdminApplication.class, args);
    }
}
