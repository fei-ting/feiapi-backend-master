package com.feiting.feiapi.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiting.feiapi.model.dto.user.UserLoginRequest;
import com.feiting.feiapi.model.dto.user.UserRegisterRequest;
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
 * UserController 集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("UserController 集成测试")
class UserControllerTest {

    @Resource
    private MockMvc mockMvc;

    @Resource
    private UserService userService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 辅助方法：注册并登录，返回已登录的 session
     */
    private MockHttpSession registerAndLogin(String account, String password) throws Exception {
        UserRegisterRequest registerRequest = new UserRegisterRequest();
        registerRequest.setUserAccount(account);
        registerRequest.setUserPassword(password);
        registerRequest.setCheckPassword(password);

        mockMvc.perform(post("/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        UserLoginRequest loginRequest = new UserLoginRequest();
        loginRequest.setUserAccount(account);
        loginRequest.setUserPassword(password);

        // 使用 MockHttpSession 并在 login 请求中携带，让 session 属性被设置
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .session(session))
                .andExpect(status().isOk());

        return session;
    }

    @Nested
    @DisplayName("POST /user/register 用户注册")
    class RegisterTests {

        @Test
        @DisplayName("正常注册返回成功")
        void shouldRegisterSuccessfully() throws Exception {
            UserRegisterRequest request = new UserRegisterRequest();
            request.setUserAccount("reguser01");
            request.setUserPassword("password123");
            request.setCheckPassword("password123");

            mockMvc.perform(post("/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isNumber());
        }

        @Test
        @DisplayName("请求体为空返回参数错误")
        void shouldFailWhenBodyNull() throws Exception {
            mockMvc.perform(post("/user/register")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("密码不一致返回参数错误")
        void shouldFailWhenPasswordsMismatch() throws Exception {
            UserRegisterRequest request = new UserRegisterRequest();
            request.setUserAccount("reguser02");
            request.setUserPassword("password123");
            request.setCheckPassword("password456");

            mockMvc.perform(post("/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }

    @Nested
    @DisplayName("POST /user/login 用户登录")
    class LoginTests {

        @Test
        @DisplayName("正确账号密码登录成功")
        void shouldLoginSuccessfully() throws Exception {
            userService.userRegister("loginctrl01", "password123", "password123");

            UserLoginRequest request = new UserLoginRequest();
            request.setUserAccount("loginctrl01");
            request.setUserPassword("password123");

            mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.userAccount").value("loginctrl01"))
                    .andExpect(jsonPath("$.data.userPassword").doesNotExist())
                    .andExpect(jsonPath("$.data.accessKey").doesNotExist())
                    .andExpect(jsonPath("$.data.secretKey").doesNotExist());
        }

        @Test
        @DisplayName("密码错误返回参数错误")
        void shouldFailWhenPasswordWrong() throws Exception {
            userService.userRegister("loginctrl02", "password123", "password123");

            UserLoginRequest request = new UserLoginRequest();
            request.setUserAccount("loginctrl02");
            request.setUserPassword("wrongpassword");

            mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }

    @Nested
    @DisplayName("POST /user/logout 用户注销")
    class LogoutTests {

        @Test
        @DisplayName("已登录用户注销成功")
        void shouldLogoutSuccessfully() throws Exception {
            MockHttpSession session = registerAndLogin("logoutctrl01", "password123");

            mockMvc.perform(post("/user/logout").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(true));
        }
    }

    @Nested
    @DisplayName("GET /user/get/login 获取当前登录用户")
    class GetLoginUserTests {

        @Test
        @DisplayName("已登录用户返回用户信息")
        void shouldReturnLoginUser() throws Exception {
            MockHttpSession session = registerAndLogin("getlogin01", "password123");

            mockMvc.perform(get("/user/get/login").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.userAccount").value("getlogin01"));
        }

        @Test
        @DisplayName("未登录返回未登录错误")
        void shouldFailWhenNotLoggedIn() throws Exception {
            mockMvc.perform(get("/user/get/login"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40100));
        }
    }

    @Nested
    @DisplayName("GET /user/get 根据id获取用户")
    class GetUserByIdTests {

        @Test
        @DisplayName("登录用户查询自己的信息成功")
        void shouldGetOwnInfo() throws Exception {
            MockHttpSession session = registerAndLogin("getbyid01", "password123");
            User user = (User) session.getAttribute("userLoginState");

            mockMvc.perform(get("/user/get")
                            .param("id", String.valueOf(user.getId()))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.userAccount").value("getbyid01"));
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            MockHttpSession session = registerAndLogin("getbyid02", "password123");

            mockMvc.perform(get("/user/get")
                            .param("id", "0")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }
}
