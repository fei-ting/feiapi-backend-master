package com.feiting.feiapi.unit.config;

import com.feiting.feiapi.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Security 配置单元测试。
 */
@DisplayName("SecurityConfig 单元测试")
class SecurityConfigTest {

    /**
     * 被测安全配置。
     */
    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    @DisplayName("本地环境配置签发非 Secure 的 CSRF Cookie")
    void shouldIssueNonSecureCsrfCookieWhenSecureSettingDisabled() {
        Cookie csrfCookie = saveCsrfCookie(false);

        assertThat(csrfCookie.getSecure()).isFalse();
        assertThat(csrfCookie.getPath()).isEqualTo("/");
        assertThat(csrfCookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    @DisplayName("生产环境配置签发 Secure 的 CSRF Cookie")
    void shouldIssueSecureCsrfCookieWhenSecureSettingEnabled() {
        Cookie csrfCookie = saveCsrfCookie(true);

        assertThat(csrfCookie.getSecure()).isTrue();
        assertThat(csrfCookie.getPath()).isEqualTo("/");
        assertThat(csrfCookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    /**
     * 使用指定 Secure 配置生成并保存 CSRF Cookie。
     *
     * @param secureCookie 是否启用 Secure 属性
     * @return 保存到响应中的 CSRF Cookie
     */
    private Cookie saveCsrfCookie(boolean secureCookie) {
        CookieCsrfTokenRepository repository = securityConfig.csrfTokenRepository(secureCookie);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken csrfToken = repository.generateToken(request);

        repository.saveToken(csrfToken, request, response);

        Cookie csrfCookie = response.getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        return csrfCookie;
    }
}
