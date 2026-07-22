package com.feiting.feiapi.controller;

import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.common.ResultUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CSRF 令牌初始化接口。
 *
 * <p>前端在发送写请求前，如果浏览器尚未持有 {@code XSRF-TOKEN} Cookie，
 * 会先调用本接口触发令牌生成。Spring Security 的 {@link CookieCsrfTokenRepository}
 * 会自动将令牌写入响应 Cookie。</p>
 *
 * <p>本接口属于安全方法（GET），本身不要求 CSRF Token。
 * 全局 {@code anyRequest().permitAll()} 已允许访问，因此不需要额外的授权 Matcher。
 * 禁止使用 {@code ignoringRequestMatchers("/csrf")}，避免未来该路径新增写方法后被整体豁免。</p>
 */
@RestController
@RequestMapping("/csrf")
public class CsrfController {

    /**
     * 初始化当前浏览器使用的 CSRF Cookie。
     *
     * <p>访问 {@link CsrfToken#getToken()} 会触发令牌生成，
     * 由 {@link CookieCsrfTokenRepository} 自动写入响应 Cookie。
     * 接口不在响应体中返回令牌内容，只返回统一成功结构。</p>
     *
     * @param csrfToken Spring Security 延迟生成的 CSRF 令牌
     * @param response  HTTP 响应，用于设置 Cache-Control
     * @return 不包含令牌内容的统一成功响应
     */
    @GetMapping
    public BaseResponse<Void> getCsrfToken(CsrfToken csrfToken, HttpServletResponse response) {
        // 访问令牌以触发生成并保存到 Cookie
        csrfToken.getToken();
        // 禁止浏览器缓存 CSRF 初始化响应
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResultUtils.success(null);
    }
}
