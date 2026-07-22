package com.feiting.feiapi.security;

import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.common.ResultUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * CSRF 校验失败专用处理器。
 *
 * <p>当写请求缺少或伪造 CSRF 令牌时，由 Spring Security 调用本处理器。
 * 返回统一 JSON 格式的 403 响应，并通过 {@link CookieCsrfTokenRepository}
 * 清除浏览器端的旧 CSRF Cookie，使下一次写操作重新初始化令牌。</p>
 *
 * <p>日志仅记录请求方法和 URI，不记录 CSRF Token、Cookie、Session ID 或请求敏感数据。</p>
 */
@Slf4j
public class CsrfAccessDeniedHandler implements AccessDeniedHandler {

    private final CookieCsrfTokenRepository csrfTokenRepository;

    private final ObjectMapper objectMapper;

    /**
     * 构造 CSRF 拒绝处理器。
     *
     * @param csrfTokenRepository 安全配置中使用的同一个 CSRF Cookie 仓库
     * @param objectMapper        应用统一的 JSON 序列化器
     */
    public CsrfAccessDeniedHandler(CookieCsrfTokenRepository csrfTokenRepository,
                                   ObjectMapper objectMapper) {
        this.csrfTokenRepository = csrfTokenRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理 CSRF 校验失败响应，并清除浏览器中的旧令牌 Cookie。
     *
     * @param request               HTTP 请求
     * @param response              HTTP 响应
     * @param accessDeniedException 访问拒绝异常
     * @throws IOException JSON 响应写入失败时抛出
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        // 记录拒绝日志，仅包含请求方法和 URI，不包含敏感信息
        log.warn("CSRF 校验失败: {} {}", request.getMethod(), request.getRequestURI());

        // 通过仓库清除浏览器端的旧 CSRF Cookie，复用统一的 Path、SameSite 和 Secure 配置
        csrfTokenRepository.saveToken(null, request, response);

        // 写入 JSON 响应
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        BaseResponse<?> body = ResultUtils.error(40300, "安全校验失败，请刷新页面后重试");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
