package com.zifang.z.surl.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * z-surl-admin Web 配置 —— SPA history fallback。
 *
 * <p>前端是 BrowserRouter，刷新 /shortlink 这类路径时 Spring 找不到静态资源就直接 404（返回 JSON），
 * 浏览器拿不到 index.html 就是白屏。vite dev / vite preview 自带 fallback，把这个洞完全掩盖了，只有线上现形。
 *
 * <p>suit 的 vite {@code base} 是 {@code /}，与 context-path 一致（该仓 007 踩坑 #19 已经钉住这一对），
 * 所以 resourcePath 恒为 {@code assets/...} / {@code api/...}。
 *
 * <p>{@code api/} 与 {@code assets/} 返回 null 是必须的：让它们照常落到 404 / 静态资源，
 * 否则 API 404 会被悄悄回成 index.html，浏览器拿 HTML 去 parse JSON，报一个跟真实原因无关的错。
 *
 * <p>前端默认不进构建（{@code -Pfrontend} 才跑），见本模块 pom。
 */
@Configuration
public class SurlWebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("assets/")) {
                            return null;
                        }
                        // 任何带扩展名的都不是前端路由：vite base 配错时 index.html 会去请求
                        // /meta/assets/x.js，回 index.html 会得到一个 200 text/html 的假成功，
                        // 浏览器报的是 "Failed to fetch dynamically imported module"，看不出是 base 错了。
                        if (resourcePath.lastIndexOf('.') > resourcePath.lastIndexOf('/')) {
                            return null;
                        }
                        return location.createRelative("index.html");
                    }
                });
    }
}
