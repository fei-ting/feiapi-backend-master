package com.feiting.feiapi.config;

import com.feiting.feiapi.security.CsrfAccessDeniedHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Spring Security 安全配置。
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>启用基于双提交 Cookie 的 CSRF 校验</li>
 *   <li>显式关闭表单登录、HTTP Basic、默认退出端点和 CORS，避免产生第二套认证及跨域行为</li>
 *   <li>所有 URL 保持 {@code permitAll}，继续由现有 AOP 和 Session 逻辑完成身份与角色鉴权</li>
 * </ul>
 *
 * @see CookieCsrfTokenRepository
 * @see CsrfAccessDeniedHandler
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * CSRF Cookie 仓库。
     *
     * <p>Cookie 属性（设计文档 7.2 节）：</p>
     * <ul>
     *   <li>名称：XSRF-TOKEN</li>
     *   <li>HttpOnly=false（Axios 需要读取）</li>
     *   <li>Path=/</li>
     *   <li>Domain 不设置（Host-only Cookie）</li>
     *   <li>SameSite=Lax</li>
     *   <li>Secure 与 Session Cookie 共用同一个环境配置</li>
     * </ul>
     *
     * @param secureCookie 是否只允许通过 HTTPS 发送 Cookie
     * @return CSRF Cookie 仓库
     */
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        // 使用部署环境的明确开关，避免 TLS 在反向代理终止后误判内部 HTTP 请求
        repository.setSecure(secureCookie);
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    /**
     * 创建 CSRF 拒绝处理器。
     *
     * @param csrfTokenRepository 安全过滤链使用的 CSRF Cookie 仓库
     * @param objectMapper        应用统一的 JSON 序列化器
     * @return CSRF 拒绝处理器
     */
    @Bean
    public CsrfAccessDeniedHandler csrfAccessDeniedHandler(
            CookieCsrfTokenRepository csrfTokenRepository,
            ObjectMapper objectMapper) {
        return new CsrfAccessDeniedHandler(csrfTokenRepository, objectMapper);
    }

    /**
     * 安全过滤链。
     *
     * <p>显式包含以下配置，不依赖 Spring Security 默认行为：</p>
     * <ul>
     *   <li>CSRF：使用 {@link CookieCsrfTokenRepository} 校验双提交 Cookie</li>
     *   <li>CSRF 请求处理器：接收 Axios 发送的原始 Header 令牌</li>
     *   <li>CSRF 拒绝处理器：返回统一 JSON 响应并清除旧 Cookie</li>
     *   <li>禁用表单登录、HTTP Basic、默认退出端点和 CORS</li>
     *   <li>所有 URL 授权规则保持 {@code permitAll}</li>
     * </ul>
     *
     * <p>{@code permitAll} 只表示 Spring Security 不接管身份授权，不会关闭 CSRF 校验。
     * 现有 AOP 和 Session 逻辑继续执行身份与角色鉴权。</p>
     *
     * @param http                    HTTP 安全构建器
     * @param csrfTokenRepository     CSRF Cookie 仓库
     * @param csrfAccessDeniedHandler CSRF 拒绝处理器
     * @return 安全过滤链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository csrfTokenRepository,
            CsrfAccessDeniedHandler csrfAccessDeniedHandler) throws Exception {
        // 使用标准处理器接收 Axios 发送的原始 Header 令牌
        CsrfTokenRequestAttributeHandler csrfTokenRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfTokenRequestHandler))
                .exceptionHandling(exception -> exception
                        .accessDeniedHandler(csrfAccessDeniedHandler))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

        return http.build();
    }
}
