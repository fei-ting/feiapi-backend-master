package com.feiting.feiapi.integration.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiting.feiapi.model.dto.user.UserLoginRequest;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.User;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthInterceptor 权限校验集成测试
 * 通过访问带 @AuthCheck 注解的端点来验证 AOP 拦截逻辑
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AuthInterceptor 权限校验集成测试")
class AuthInterceptorTest {

    @Resource
    private MockMvc mockMvc;

    @Resource
    private UserService userService;

    @Resource
    private ObjectMapper objectMapper;

    private MockHttpSession loginWithRole(String account, String role) throws Exception {
        userService.userRegister(account, "password123", "password123");
        User user = userService.lambdaQuery().eq(User::getUserAccount, account).one();
        user.setUserRole(role);
        userService.updateById(user);

        UserLoginRequest loginRequest = new UserLoginRequest();
        loginRequest.setUserAccount(account);
        loginRequest.setUserPassword("password123");
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .session(session))
                .andExpect(status().isOk());
        return session;
    }

    @Nested
    @DisplayName("@AuthCheck(mustRole=admin) 管理员权限校验")
    class MustRoleAdminTests {

        @Test
        @DisplayName("管理员访问 /user/list/page 成功")
        void shouldAllowAdminAccess() throws Exception {
            MockHttpSession session = loginWithRole("aut01", "admin");

            mockMvc.perform(get("/user/list/page").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("普通用户访问 /user/list/page 返回无权限")
        void shouldDenyNormalUser() throws Exception {
            MockHttpSession session = loginWithRole("auu01", "user");

            mockMvc.perform(get("/user/list/page").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));
        }

        @Test
        @DisplayName("未登录访问 /user/list/page 返回未登录")
        void shouldDenyNotLoggedIn() throws Exception {
            mockMvc.perform(get("/user/list/page"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40100));
        }

    }
}
