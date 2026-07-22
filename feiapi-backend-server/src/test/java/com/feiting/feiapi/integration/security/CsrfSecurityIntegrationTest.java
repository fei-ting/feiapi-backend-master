package com.feiting.feiapi.integration.security;

import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF、Cookie 与跨域安全边界集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("CSRF 安全边界集成测试")
class CsrfSecurityIntegrationTest {

    /**
     * CSRF Cookie 名称。
     */
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";

    /**
     * CSRF 请求头名称。
     */
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    /**
     * 被测 MVC 请求入口。
     */
    @Resource
    private MockMvc mockMvc;

    /**
     * 应用实际使用的 Spring Security 过滤链代理。
     */
    @Resource
    private FilterChainProxy springSecurityFilterChain;

    /**
     * 应用配置的 CSRF Cookie 仓库。
     */
    @Resource
    private CookieCsrfTokenRepository csrfTokenRepository;

    /**
     * 在每个专项用例前恢复应用配置的 Cookie 仓库。
     *
     * <p>Spring Security Test 的 {@code csrf()} 后处理器会通过反射替换共享过滤器仓库，
     * 且不会在请求结束后恢复。既有 Controller 测试使用该后处理器，因此这里显式隔离测试状态。</p>
     */
    @BeforeEach
    void restoreConfiguredCsrfTokenRepository() {
        CsrfFilter csrfFilter = findCsrfFilter();
        ReflectionTestUtils.setField(csrfFilter, "tokenRepository", csrfTokenRepository);
    }

    @Test
    @DisplayName("安全过滤链使用应用配置的 CSRF Cookie 仓库")
    void shouldUseConfiguredCookieCsrfTokenRepository() {
        CsrfFilter csrfFilter = findCsrfFilter();

        Object actualRepository = ReflectionTestUtils.getField(csrfFilter, "tokenRepository");

        assertThat(actualRepository).isSameAs(csrfTokenRepository);
    }

    /**
     * 从应用安全过滤链中获取 CSRF 过滤器。
     *
     * @return 应用使用的 CSRF 过滤器
     */
    private CsrfFilter findCsrfFilter() {
        return springSecurityFilterChain.getFilters("/csrf").stream()
                .filter(CsrfFilter.class::isInstance)
                .map(CsrfFilter.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("写请求缺少令牌时返回统一 403 响应并清除旧 Cookie")
    void shouldRejectWriteRequestWithoutTokenAndExpireCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300))
                .andExpect(jsonPath("$.message").value("安全校验失败，请刷新页面后重试"))
                .andReturn();

        Cookie expiredCookie = findExpiredCsrfCookie(result);
        assertThat(expiredCookie.getMaxAge()).isZero();
        assertThat(expiredCookie.getPath()).isEqualTo("/");
        assertThat(expiredCookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    @DisplayName("Cookie 与 Header 都为空时写请求返回 403")
    void shouldRejectWriteRequestWithEmptyCookieAndHeader() throws Exception {
        mockMvc.perform(post("/user/register")
                        .cookie(new Cookie(CSRF_COOKIE_NAME, ""))
                        .header(CSRF_HEADER_NAME, "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @DisplayName("Cookie 与 Header 不一致时写请求返回 403")
    void shouldRejectWriteRequestWithMismatchedCookieAndHeader() throws Exception {
        Cookie csrfCookie = requestCsrfCookie();

        mockMvc.perform(post("/user/register")
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue() + "-tampered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @DisplayName("Cookie 与 Header 一致时写请求可以进入 Controller")
    void shouldAllowWriteRequestWithMatchingCookieAndHeader() throws Exception {
        Cookie csrfCookie = requestCsrfCookie();

        mockMvc.perform(post("/user/register")
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @DisplayName("CSRF 初始化接口返回统一结构且不泄露令牌")
    void shouldInitializeCsrfCookieWithoutReturningTokenInBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        Cookie csrfCookie = parseCsrfCookie(result);
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(csrfCookie.getValue());
    }

    @Test
    @DisplayName("基础环境 CSRF Cookie 使用约定属性且保持 Host-only")
    void shouldIssueExpectedCsrfCookieAttributesInBaseEnvironment() throws Exception {
        Cookie csrfCookie = requestCsrfCookie();

        assertThat(csrfCookie.getName()).isEqualTo(CSRF_COOKIE_NAME);
        assertThat(csrfCookie.getPath()).isEqualTo("/");
        assertThat(csrfCookie.getDomain()).isNull();
        assertThat(csrfCookie.isHttpOnly()).isFalse();
        assertThat(csrfCookie.getSecure()).isFalse();
        assertThat(csrfCookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    @DisplayName("非同源请求不返回跨域允许响应头")
    void shouldNotReturnCorsHeadersForCrossOriginRequest() throws Exception {
        mockMvc.perform(get("/csrf")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    /**
     * 请求 CSRF 初始化接口并解析响应 Cookie。
     *
     * @return 初始化接口签发的 CSRF Cookie
     * @throws Exception 请求执行失败时抛出
     */
    private Cookie requestCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return parseCsrfCookie(result);
    }

    /**
     * 从 MockMvc 响应中解析 CSRF Set-Cookie 响应头。
     *
     * @param result MVC 请求结果
     * @return 解析后的 CSRF Cookie
     */
    private Cookie parseCsrfCookie(MvcResult result) {
        Cookie csrfCookie = result.getResponse().getCookie(CSRF_COOKIE_NAME);
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getName()).isEqualTo(CSRF_COOKIE_NAME);
        return csrfCookie;
    }

    /**
     * 从拒绝响应中查找用于删除旧令牌的 CSRF Cookie。
     *
     * @param result MVC 请求结果
     * @return Max-Age 为 0 的 CSRF Cookie
     */
    private Cookie findExpiredCsrfCookie(MvcResult result) {
        return java.util.Arrays.stream(result.getResponse().getCookies())
                .filter(cookie -> CSRF_COOKIE_NAME.equals(cookie.getName()))
                .filter(cookie -> cookie.getMaxAge() == 0)
                .findFirst()
                .orElseThrow();
    }
}
