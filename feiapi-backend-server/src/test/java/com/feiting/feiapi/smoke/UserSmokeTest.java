package com.feiting.feiapi.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiting.feiapi.model.dto.user.UserLoginRequest;
import com.feiting.feiapi.model.dto.user.UserRegisterRequest;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.User;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户模块冒烟测试
 * 单个方法内完成 注册 -> 登录 -> 获取登录用户 -> 注销 -> 验证注销 的完整链路
 * 不使用 @Transactional，数据真实落库，最后手动清理
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("用户模块冒烟测试")
class UserSmokeTest {

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private UserService userService;

    @Test
    @DisplayName("完整链路: 注册 -> 登录 -> 获取登录用户 -> 注销 -> 验证注销")
    void fullUserLifecycle() throws Exception {
        String account = "smoke_lifecycle_" + System.currentTimeMillis();
        String password = "password123";
        long userId = -1;

        try {
            // ======== Step1: 注册 ========
            UserRegisterRequest registerRequest = new UserRegisterRequest();
            registerRequest.setUserAccount(account);
            registerRequest.setUserPassword(password);
            registerRequest.setCheckPassword(password);

            MvcResult registerResult = mockMvc.perform(post("/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isNumber())
                    .andReturn();

            userId = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                    .get("data").asLong();
            assertTrue(userId > 0, "注册返回的用户 id 应大于 0");

            // ======== Step2: 登录 ========
            UserLoginRequest loginRequest = new UserLoginRequest();
            loginRequest.setUserAccount(account);
            loginRequest.setUserPassword(password);

            MockHttpSession session = new MockHttpSession();
            MvcResult loginResult = mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            // 验证登录返回的用户信息
            JsonNode loginData = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                    .get("data");
            assertEquals(account, loginData.get("userAccount").asText());
            assertFalse(loginData.has("accessKey"), "登录响应不应返回 accessKey");
            assertFalse(loginData.has("secretKey"), "登录响应不应返回 secretKey");

            // 验证 session 中确实存储了用户
            Object sessionUser = session.getAttribute("userLoginState");
            assertNotNull(sessionUser, "登录后 session 中应存储用户信息");

            // ======== Step3: 获取当前登录用户 ========
            MvcResult getLoginUserResult = mockMvc.perform(get("/user/get/login").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            JsonNode loginUser = objectMapper.readTree(getLoginUserResult.getResponse().getContentAsString())
                    .get("data");
            assertEquals(userId, loginUser.get("id").asLong(), "获取的用户 id 应与注册的一致");
            assertEquals(account, loginUser.get("userAccount").asText());

            // ======== Step4: 注销 ========
            mockMvc.perform(post("/user/logout").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(true));

            // 验证 session 中用户已被清除
            assertNull(session.getAttribute("userLoginState"), "注销后 session 中应无用户信息");

            // ======== Step5: 注销后获取登录用户应返回未登录 ========
            mockMvc.perform(get("/user/get/login").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40100));
        } finally {
            // 无论断言是否失败，都清理数据
            if (userId > 0) {
                userService.removeById(userId);
            }
        }
    }

    @Test
    @DisplayName("注册重复账号应失败")
    void registerDuplicateAccountShouldFail() throws Exception {
        String account = "smoke_dup_" + System.currentTimeMillis();
        long userId = -1;

        try {
            // 第一次注册
            UserRegisterRequest request = new UserRegisterRequest();
            request.setUserAccount(account);
            request.setUserPassword("password123");
            request.setCheckPassword("password123");

            MvcResult first = mockMvc.perform(post("/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andReturn();

            userId = objectMapper.readTree(first.getResponse().getContentAsString())
                    .get("data").asLong();

            // 第二次注册同账号应失败
            mockMvc.perform(post("/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        } finally {
            if (userId > 0) {
                userService.removeById(userId);
            }
        }
    }

    @Test
    @DisplayName("错误密码登录应失败")
    void loginWithWrongPasswordShouldFail() throws Exception {
        String account = "smoke_wrongpw_" + System.currentTimeMillis();
        long userId = -1;

        try {
            // 注册
            UserRegisterRequest registerRequest = new UserRegisterRequest();
            registerRequest.setUserAccount(account);
            registerRequest.setUserPassword("password123");
            registerRequest.setCheckPassword("password123");

            MvcResult regResult = mockMvc.perform(post("/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(registerRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            userId = objectMapper.readTree(regResult.getResponse().getContentAsString())
                    .get("data").asLong();

            // 错误密码登录
            UserLoginRequest loginRequest = new UserLoginRequest();
            loginRequest.setUserAccount(account);
            loginRequest.setUserPassword("wrongpassword");

            mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        } finally {
            if (userId > 0) {
                userService.removeById(userId);
            }
        }
    }
}
