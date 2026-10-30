package com.zifang.z.surl.web;

import com.zifang.z.surl.domain.entity.SurlMappingDO;
import com.zifang.z.surl.service.SurlService;
import com.zifang.z.surl.web.request.SurlShortenRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SurlController} 回归测试。
 * <p>
 * 病灶：{@code SurlShortenRequest} 上声明了 {@code @Size(max = 2048)}，而 origin_url 的
 * DDL 列宽正是 {@code VARCHAR(2048)}；但 controller 的 shorten() 参数<b>没有挂 {@code @Valid}</b>，
 * 于是这两道约束一次都没被触发过 —— 超长 URL 在严格模式下抛 SQLException 变 500，
 * 非严格模式下被静默截断，短链跳转到被截断的 URL。两种都不是本该返回的 400。
 */
class SurlControllerValidationTest {

    /** 最短路径：反射确认 @Valid 真的挂在参数上。 */
    @Test
    void shortenRequestIsAnnotatedWithValid() throws Exception {
        Method m = SurlController.class.getMethod(
                "shorten", SurlShortenRequest.class, String.class);
        Parameter p = m.getParameters()[0];

        assertTrue(p.isAnnotationPresent(Valid.class),
                "shorten() 的 request 参数必须挂 @Valid，否则 @NotBlank/@Size 永不触发");

        // 同时确认 RequestBody 仍在（body 可空语义不能被改掉）
        assertTrue(p.isAnnotationPresent(RequestBody.class),
                "shorten() 的 request 参数必须保留 @RequestBody");
    }

    /** request 上的 @Size 约束必须与 DDL 列宽一致，否则就是上面那个缺口。 */
    @Test
    void originUrlSizeMatchesDdlColumnWidth() throws Exception {
        Size size = SurlShortenRequest.class.getDeclaredField("originUrl").getAnnotation(Size.class);
        assertNotNull(size, "originUrl 必须声明 @Size");
        assertEquals(2048, size.max(),
                "@Size(max) 必须等于 DDL 的 VARCHAR(2048)，否则要么 500 要么被截断");
    }

    // ---------- 行为测试（不依赖 Spring 容器，直接 new controller） ----------

    /** 记Calls 的 SurlService 桩。 */
    static class StubService implements SurlService {
        String storedKey = "Ab12CdEf";
        SurlMappingDO record;
        int shortenCalls;
        int infoCalls;
        int invalidateCalls;
        boolean resolvePresent = true;

        StubService() {
            record = new SurlMappingDO();
            record.setMapKey(storedKey);
            record.setOriginUrl("https://example.com/a");
            record.setExpireAt(LocalDateTime.now().plusDays(1));
            record.setHits(0L);
            record.setTenantCode("tenantA");
        }

        @Override
        public String shorten(String originUrl, String tenantCode, long ttlMillis,
                              String createdBy, String remark) {
            shortenCalls++;
            return storedKey;
        }

        @Override
        public Optional<SurlMappingDO> resolve(String mapKey) {
            return resolvePresent ? Optional.of(record) : Optional.empty();
        }

        @Override
        public Optional<SurlMappingDO> info(String mapKey) {
            infoCalls++;
            return Optional.of(record);
        }

        @Override
        public boolean invalidate(String mapKey) {
            invalidateCalls++;
            return true;
        }

    }

    @Test
    void shortenWithNullBodyReturns400() {
        SurlController c = new SurlController(new StubService());
        assertEquals(400, c.shorten(null, null).getCode());
    }

    @Test
    void shortenWithBlankUrlReturns400() {
        SurlController c = new SurlController(new StubService());
        SurlShortenRequest r = new SurlShortenRequest();
        r.setOriginUrl("   ");
        assertEquals(400, c.shorten(r, null).getCode());
    }

    @Test
    void shortenReachesServiceWhenUrlPresent() {
        StubService svc = new StubService();
        SurlController c = new SurlController(svc);
        SurlShortenRequest r = new SurlShortenRequest();
        r.setOriginUrl("https://example.com/a");
        c.shorten(r, "7");
        assertEquals(1, svc.shortenCalls);
    }

    @Test
    void resolveReturns302WithLocationHeader() {
        StubService svc = new StubService();
        SurlController c = new SurlController(svc);
        ResponseEntity<?> resp = c.resolve("Ab12CdEf", null);
        assertEquals(HttpStatus.FOUND, resp.getStatusCode());
        assertNotNull(resp.getHeaders().getLocation(), "302 必须带 Location 头");
        assertEquals("https://example.com/a", resp.getHeaders().getLocation().toString());
    }

    @Test
    void resolveMismatchedTenantReturns404NotFound() {
        // 防探测：租户不匹配必须返回 404，不能是 403（403 会泄露"该短码存在"）
        StubService svc = new StubService();
        SurlController c = new SurlController(svc);
        ResponseEntity<?> resp = c.resolve("Ab12CdEf", "tenantB");
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void resolveMatchingTenantRedirects() {
        StubService svc = new StubService();
        SurlController c = new SurlController(svc);
        assertEquals(HttpStatus.FOUND, c.resolve("Ab12CdEf", "tenantA").getStatusCode());
    }

    @Test
    void resolveUnknownKeyReturns404() {
        StubService svc = new StubService();
        svc.resolvePresent = false;
        SurlController c = new SurlController(svc);
        assertEquals(HttpStatus.NOT_FOUND, c.resolve("nope", null).getStatusCode());
    }

    @Test
    void urlContractPathsAreUnchanged() {
        // 类 Javadoc 明确写了路径里的 ctc 不能改（控制台 + 部署健康检查两处活依赖）
        Method shorten = null;
        try {
            shorten = SurlController.class.getMethod("shorten", SurlShortenRequest.class, String.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
        org.springframework.web.bind.annotation.PostMapping pm =
                shorten.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
        assertNotNull(pm, "shorten 必须保留 @PostMapping");
        assertEquals("/api/ctc/surl/shorten", pm.value()[0], "URL 契约不能改");

        Method resolve = null;
        try {
            resolve = SurlController.class.getMethod("resolve", String.class, String.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
        org.springframework.web.bind.annotation.GetMapping gm =
                resolve.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
        assertNotNull(gm, "resolve 必须保留 @GetMapping");
        assertEquals("/s/{mapKey}", gm.value()[0], "跳转路径不能改");
        assertFalse(gm.value()[0].contains("api"), "跳转走短链路径 /s/{mapKey}，不是管理面路径");
    }
}
