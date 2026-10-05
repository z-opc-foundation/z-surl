package com.zifang.z.surl.web;

import com.zifang.util.core.meta.Result;
import com.zifang.util.core.meta.ResultCode;
import com.zifang.z.surl.domain.entity.SurlMappingDO;
import com.zifang.z.surl.service.SurlService;
import com.zifang.z.surl.web.request.SurlShortenRequest;
import com.zifang.z.surl.web.response.SurlShortenResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.net.URI;
import java.util.Optional;

/**
 * 短网址 Controller.
 * <h3>URL 契约（不要改）</h3>
 * 管理面 {@code /api/ctc/surl/**} + 跳转面 {@code /s/{mapKey}}。
 * <p>
 * <b>路径里的 {@code ctc} 是历史遗留</b>（本仓从 z-ctc 抽出时沿用），与新仓名 z-surl 不一致。
 * 但它<b>不能改</b>，实测有两处活依赖：
 * <ol>
 *   <li>{@code z-opc/bootstraps/z-opc-main-starter-frontend/src/ctc/components/SurlManagement/api.js}
 *       —— 线上控制台页面按这组路径发请求；</li>
 *   <li>{@code z-opc/_doc/005_skills/z-opc-deploy-250/} 的部署健康检查
 *       （{@code curl http://192.168.31.250:8888/api/ctc/surl/shorten}）。</li>
 * </ol>
 * 改路径会让控制台 404、健康检查判死。对齐动作按 007 文档步骤 6「URL/param 保持不变」执行。
 * 若将来要收敛成 {@code /api/zSurl/**}，必须**同一次变更**里改上面两处调用方 + 控制台配置。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>{@code POST   /api/ctc/surl/shorten} —— 生成短码</li>
 *   <li>{@code GET    /api/ctc/surl/info?mapKey=xxx} —— 查询元信息（请求参数）</li>
 *   <li>{@code DELETE /api/ctc/surl?mapKey=xxx} —— 失效（请求参数）</li>
 *   <li>{@code GET    /s/{mapKey}} —— 解析并 302 跳转（路径变量，短链场景要求短 URL）</li>
 * </ul>
 *
 * <h3>关键约束</h3>
 * <ul>
 *   <li>跳转走 {@code /s/{mapKey}} 而非 {@code /api/ctc/surl/{mapKey}}：用户看到的最终短链
 *       形如 {@code https://your.host/s/Ab12CdEf}，短、可读、可直接贴进聊天窗口</li>
 *   <li>命中 +1 在 resolve 时累加；invalidate 后 resolve 立即 404</li>
 *   <li>resolve 支持 tenant 强制匹配：调用方传入的 tenant 与短码归属租户不一致时返回 404
 *       （返回 404 而不是 403，避免探测出"该短码存在但不属于你"）</li>
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
     * <p>
     * <b>{@code @Valid} 不能去掉</b>: {@link SurlShortenRequest} 上声明了
     * {@code @NotBlank} 与 {@code @Size(max = 2048)}, 而 origin_url 的 DDL 列宽正是
     * {@code VARCHAR(2048)}。少了 @Valid 这两道约束<b>一次都不会被触发</b> ——
     * 超过 2048 的 URL 在严格模式下抛 SQLException 变 500，非严格模式下被静默截断，
     * 结果是短链跳转到【被截断的 URL】。两种都不是本该返回的 400。
     * <p>
     * body 仍标 {@code required = false}（缺 body 走不到校验器），所以判空仍需下面手写。
     *
     * @param request 含 originUrl / tenantCode / ttlMillis / remark 的请求体
     * @param userId  创建者账号 id, 来自请求头 X-User-Id, 可选
     * @return 含 mapKey / originUrl / expireAt / hits 的 SurlShortenResponse (HTTP 201)
     */
    @PostMapping("/api/ctc/surl/shorten")
    public Result<SurlShortenResponse> shorten(
            @Valid @RequestBody(required = false) SurlShortenRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        // body 标了 required=false, 校验器不会触发, 所以这里显式兜: 缺 body / 缺 url 一律 400, 不落到 500
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
     * 把短码标记为失效 —— 后续 resolve 立即返回 404.
     * <p>
     * 语义边界: 本实现是物理删除。短链场景下<b>已发出的链接无法回收</b>（对方可能已收藏/已投放到
     * 第三方系统），"失效"只保证后续解析立即 404。
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
     * 短链跳转入口 —— 命中次数 +1, 返回 302 + Location 头指向 originUrl. 失败返回 404 JSON.
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
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
