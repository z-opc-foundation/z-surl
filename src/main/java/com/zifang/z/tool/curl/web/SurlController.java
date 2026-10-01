package com.zifang.z.tool.curl.web;

import com.zifang.util.core.meta.Result;
import com.zifang.util.core.meta.ResultCode;
import com.zifang.z.tool.curl.domain.entity.SurlMappingDO;
import com.zifang.z.tool.curl.service.SurlService;
import com.zifang.z.tool.curl.vo.SurlMappingVO;
import com.zifang.z.tool.curl.web.request.SurlShortenRequest;
import com.zifang.z.tool.curl.web.response.SurlShortenResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

/**
 * 短网址 Controller.
 * <p>
 * API 基础路径: /api/ctc/surl (管理面) + /s (短链跳转面)
 * 所属模块: z-ctc-surl
 * 鉴权: 短链跳转接口 (GET /s/{mapKey}) 公开, 其它接口由 SsoInterceptor 统一拦截
 *
 * <p>端点:
 * <ul>
 *   <li>POST /api/ctc/surl/shorten — 生成短码</li>
 *   <li>GET /api/ctc/surl/info?mapKey=xxx — 查询元信息 (请求参数)</li>
 *   <li>GET /s/{mapKey} — 解析并 302 跳转 (路径变量, 短链场景要求短 URL)</li>
 *   <li>DELETE /api/ctc/surl?mapKey=xxx — 失效 (请求参数)</li>
 * </ul>
 * <p>
 * 短链跳转路径走 /s/{mapKey} 而非 /api/ctc/surl/{mapKey} — 业务方部署时反代到本服务,
 * 用户访问的最终短链形如 https://your.host/s/Ab12CdEf, 简短可读.
 *
 * <p>设计说明/关键约束:
 * <ul>
 *   <li>命中次数 +1 在 resolve 时累加, 短链失效 (invalidate) 后 resolve 立即返回 404</li>
 *   <li>resolve 支持 tenant 强制匹配: 调用方传入的 tenant 必须与短码归属租户一致, 否则 404</li>
 *   <li>短码生成在 Service 层用 base62 + 随机重试, 不暴露 ID 直接编码以避免顺序泄露</li>
 * </ul>
 */
@RestController
@RequestMapping
public class SurlController {

    private static final Logger log = LogManager.getLogger(SurlController.class);

    private final SurlService surlService;

    public SurlController(SurlService surlService) {
        this.surlService = surlService;
    }

    /**
     * 把长链接缩短成短码. ttlMillis 决定过期时长, 0 或 null 表示永不过期.
     *
     * @param request 含 originUrl / tenantCode / ttlMillis / remark 的请求体
     * @param userId  创建者账号 id, 来自请求头 X-User-Id, 可选
     * @return 含 mapKey / originUrl / expireAt / hits 的 SurlShortenResponse (HTTP 201)
     */
    @PostMapping("/api/ctc/surl/shorten")
    public Result<SurlShortenResponse> shorten(
            @RequestBody(required = false) SurlShortenRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        // === P2 修复: 缺请求体 / 缺 originUrl 显式 400, 不再 500 ===
        if (request == null) {
            return Result.<SurlShortenResponse>fail("请求体不能为空").code(400);
        }
        if (request.getOriginUrl() == null || request.getOriginUrl().trim().isEmpty()) {
            return Result.<SurlShortenResponse>fail("originUrl must not be blank").code(400);
        }
        long ttl = request.getTtlMillis() == null ? 0L : request.getTtlMillis();
        String mapKey = surlService.shorten(
                request.getOriginUrl(),
                request.getTenantCode(),
                ttl,
                userId,
                request.getRemark());
        SurlMappingDO record = surlService.info(mapKey)
                .orElseThrow(() -> new IllegalStateException("刚刚生成的短码查询不到: " + mapKey));
        SurlShortenResponse resp = new SurlShortenResponse(
                record.getMapKey(),
                record.getOriginUrl(),
                record.getExpireAt(),
                record.getHits());
        return Result.success(resp).code(201);
    }

    /**
     * 查询短码的元信息 (含 originUrl / 命中次数 / 过期时间 / 所属租户). 不存在返回 404.
     *
     * @param mapKey 短码, 请求参数 (?mapKey=xxx)
     * @return SurlMappingVO; 不存在返回 404
     */
    @GetMapping("/api/ctc/surl/info")
    public Result<SurlMappingVO> info(@RequestParam("mapKey") String mapKey) {
        return surlService.info(mapKey)
                .map(t -> Result.success(SurlMappingVO.from(t)))
                .orElse(Result.<SurlMappingVO>fail("资源不存在").code(ResultCode.NOT_FOUND.getCode()));
    }

    /**
     * 把短码标记为失效 — 后续 resolve 立即返回 404, 不影响已发出的链接 (短链场景下无法回收已发出的引用).
     *
     * @param mapKey 短码, 请求参数 (?mapKey=xxx)
     * @return 成功返回空体, 资源不存在返回 404
     */
    @DeleteMapping("/api/ctc/surl")
    public Result<Object> invalidate(@RequestParam("mapKey") String mapKey) {
        boolean ok = surlService.invalidate(mapKey);
        return ok ? Result.success() : Result.fail("资源不存在").code(ResultCode.NOT_FOUND.getCode());
    }

    /**
     * 短链跳转入口 — 命中次数 +1, 返回 302 + Location 头指向 originUrl. 失败返回 404 JSON.
     *
     * @param mapKey 短码, 路径变量
     * @param tenant 可选, 强制租户匹配: 传入时必须与短码归属租户一致才允许跳转, 否则 404
     * @return 成功时返回真正的 HTTP 302 ResponseEntity (Location 头指向 originUrl);
     * 浏览器/curl 直接跳转, 不需要前端解析 JSON
     */
    @GetMapping("/s/{mapKey}")
    public ResponseEntity<?> resolve(@PathVariable("mapKey") String mapKey,
                                     @RequestParam(value = "tenant", required = false) String tenant) {
        Optional<SurlMappingDO> resolved = surlService.resolve(mapKey);
        if (!resolved.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.fail("资源不存在").code(ResultCode.NOT_FOUND.getCode()));
        }
        SurlMappingDO record = resolved.get();
        if (tenant != null && !tenant.isEmpty() && record.getTenantCode() != null
                && !record.getTenantCode().equals(tenant)) {
            log.debug("SurlController.resolve 租户不匹配: mapKey={}, requested={}, actual={}",
                    mapKey, tenant, record.getTenantCode());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.fail("资源不存在").code(ResultCode.NOT_FOUND.getCode()));
        }
        log.debug("SurlController.resolve: mapKey={} → {}", mapKey, record.getOriginUrl());
        // 真正的 302 redirect: 直接返回 ResponseEntity, 浏览器自动跳转
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(record.getOriginUrl()));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);  // 302
    }
}
