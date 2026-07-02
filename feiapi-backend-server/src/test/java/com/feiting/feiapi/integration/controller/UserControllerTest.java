package com.feiting.feiapi.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiting.feiapi.controller.UserController;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.constant.UserConstant;
import com.feiting.feiapi.mapper.UserRoleChangeLogMapper;
import com.feiting.feiapi.model.dto.user.UserLoginRequest;
import com.feiting.feiapi.model.dto.user.UserQueryRequest;
import com.feiting.feiapi.model.dto.user.UserRegisterRequest;
import com.feiting.feiapi.model.enums.UserRoleEnum;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.entity.UserRoleChangeLog;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    private UserRoleChangeLogMapper userRoleChangeLogMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private UserController userController;

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

    /**
     * 辅助方法：注册并登录管理员用户，返回已登录的 session
     */
    private MockHttpSession registerAndLoginAdmin(String account, String password) throws Exception {
        MockHttpSession session = registerAndLogin(account, password);
        User user = (User) session.getAttribute(UserConstant.USER_LOGIN_STATE);
        user.setUserRole(UserConstant.ADMIN_ROLE);
        userService.updateById(user);
        session.setAttribute(UserConstant.USER_LOGIN_STATE, user);
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
             userService.userRegister("loginctl01", "password123", "password123");

            UserLoginRequest request = new UserLoginRequest();
            request.setUserAccount("loginctl01");
            request.setUserPassword("password123");

            mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.userAccount").value("loginctl01"))
                    .andExpect(jsonPath("$.data.userPassword").doesNotExist())
                    .andExpect(jsonPath("$.data.accessKey").doesNotExist())
                    .andExpect(jsonPath("$.data.secretKey").doesNotExist());
        }

        @Test
        @DisplayName("密码错误返回参数错误")
        void shouldFailWhenPasswordWrong() throws Exception {
            userService.userRegister("loginctl02", "password123", "password123");

            UserLoginRequest request = new UserLoginRequest();
            request.setUserAccount("loginctl02");
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
            MockHttpSession session = registerAndLogin("logoutc01", "password123");

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
                    .andExpect(jsonPath("$.data.userAccount").value("getlogin01"))
                    .andExpect(jsonPath("$.data.accessKey").doesNotExist())
                    .andExpect(jsonPath("$.data.secretKey").doesNotExist());
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
    @DisplayName("GET /user/get/keys 获取当前用户访问密钥")
    class GetCurrentUserKeysTests {

        @Test
        @DisplayName("已登录用户返回访问密钥")
        void shouldReturnCurrentUserKeys() throws Exception {
            MockHttpSession session = registerAndLogin("getkeys01", "password123");

            mockMvc.perform(get("/user/get/keys").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.accessKey").isNotEmpty())
                    .andExpect(jsonPath("$.data.secretKey").isNotEmpty());
        }

        @Test
        @DisplayName("未登录访问密钥接口返回未登录错误")
        void shouldFailWhenNotLoggedIn() throws Exception {
            mockMvc.perform(get("/user/get/keys"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40100));
        }
    }

    @Nested
    @DisplayName("POST /user/update/my/profile 更新当前用户个人资料")
    class UpdateCurrentUserProfileTests {

        @Test
        @DisplayName("已登录用户只能更新自己的昵称和性别")
        void shouldUpdateOnlyCurrentUserProfile() throws Exception {
            MockHttpSession session = registerAndLogin("profile01", "password123");
            User loginUser = (User) session.getAttribute(UserConstant.USER_LOGIN_STATE);
            long otherUserId = userService.userRegister("profile02", "password123", "password123");
            User otherUser = userService.getById(otherUserId);
            otherUser.setUserName("未修改用户");
            otherUser.setGender(0);
            userService.updateById(otherUser);

            String requestBody = "{\"userName\":\"新的昵称\",\"gender\":1}";

            mockMvc.perform(post("/user/update/my/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(true));

            User updatedLoginUser = userService.getById(loginUser.getId());
            User updatedOtherUser = userService.getById(otherUserId);
            org.assertj.core.api.Assertions.assertThat(updatedLoginUser.getUserName())
                    .as("当前登录用户昵称应被更新")
                    .isEqualTo("新的昵称");
            org.assertj.core.api.Assertions.assertThat(updatedLoginUser.getGender())
                    .as("当前登录用户性别应被更新")
                    .isEqualTo(1);
            org.assertj.core.api.Assertions.assertThat(updatedOtherUser.getUserName())
                    .as("其他用户昵称不应被更新")
                    .isEqualTo("未修改用户");
            org.assertj.core.api.Assertions.assertThat(updatedOtherUser.getGender())
                    .as("其他用户性别不应被更新")
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("空昵称返回参数错误")
        void shouldRejectBlankUserName() throws Exception {
            MockHttpSession session = registerAndLogin("profile03", "password123");
            String requestBody = "{\"userName\":\" \",\"gender\":1}";

            mockMvc.perform(post("/user/update/my/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("非法性别返回参数错误")
        void shouldRejectInvalidGender() throws Exception {
            MockHttpSession session = registerAndLogin("profile04", "password123");
            String requestBody = "{\"userName\":\"昵称\",\"gender\":2}";

            mockMvc.perform(post("/user/update/my/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("未登录返回未登录错误")
        void shouldRejectProfileUpdateWhenNotLoggedIn() throws Exception {
            String requestBody = "{\"userName\":\"昵称\",\"gender\":1}";

            mockMvc.perform(post("/user/update/my/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40100));
        }
    }

    @Nested
    @DisplayName("POST /user/update/my/password 更新当前用户密码")
    class UpdateCurrentUserPasswordTests {

        @Test
        @DisplayName("旧密码正确且新密码合法时更新成功")
        void shouldUpdatePasswordWhenOldPasswordMatches() throws Exception {
            MockHttpSession session = registerAndLogin("pwdctl01", "password123");
            String requestBody = "{\"oldPassword\":\"password123\",\"newPassword\":\"password456\",\"checkPassword\":\"password456\"}";

            mockMvc.perform(post("/user/update/my/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(true));

            UserLoginRequest loginRequest = new UserLoginRequest();
            loginRequest.setUserAccount("pwdctl01");
            loginRequest.setUserPassword("password456");

            mockMvc.perform(post("/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("旧密码错误返回参数错误")
        void shouldRejectWrongOldPassword() throws Exception {
            MockHttpSession session = registerAndLogin("pwdctl02", "password123");
            String requestBody = "{\"oldPassword\":\"password456\",\"newPassword\":\"password789\",\"checkPassword\":\"password789\"}";

            mockMvc.perform(post("/user/update/my/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("两次新密码不一致返回参数错误")
        void shouldRejectMismatchedNewPassword() throws Exception {
            MockHttpSession session = registerAndLogin("pwdctl03", "password123");
            String requestBody = "{\"oldPassword\":\"password123\",\"newPassword\":\"password456\",\"checkPassword\":\"password789\"}";

            mockMvc.perform(post("/user/update/my/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("非法新密码返回参数错误")
        void shouldRejectInvalidNewPassword() throws Exception {
            MockHttpSession session = registerAndLogin("pwdctl04", "password123");
            String requestBody = "{\"oldPassword\":\"password123\",\"newPassword\":\"short\",\"checkPassword\":\"short\"}";

            mockMvc.perform(post("/user/update/my/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }

    @Nested
    @DisplayName("POST /user/avatar/upload 上传当前用户头像")
    class UploadCurrentUserAvatarTests {

        @Test
        @DisplayName("OSS 未接入时返回明确业务错误")
        void shouldReturnPlaceholderErrorWhenOssNotConfigured() throws Exception {
            MockHttpSession session = registerAndLogin("avatar01", "password123");
            MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "avatar".getBytes());

            mockMvc.perform(multipart("/user/avatar/upload")
                            .file(file)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(50001))
                    .andExpect(jsonPath("$.message").value("对象存储暂未接入"));
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

        @Test
        @DisplayName("管理员查询超过 int 范围的用户 id 成功")
        void shouldGetUserWhenIdExceedsIntegerMaxValue() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("getbyid03", "password123");
            Long largeUserId = Integer.MAX_VALUE + 1L;
            User user = new User();
            user.setId(largeUserId);
            user.setUserName("大 ID 用户");
            user.setUserAccount("largeid01");
            user.setUserPassword(userService.encodePassword("password123"));
            user.setUserRole(UserConstant.DEFAULT_ROLE);
            userService.save(user);

            mockMvc.perform(get("/user/get")
                            .param("id", String.valueOf(largeUserId))
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(largeUserId))
                    .andExpect(jsonPath("$.data.userAccount").value("largeid01"));
        }
    }

    @Nested
    @DisplayName("GET /user/list/page 分页获取用户列表")
    class ListUserByPageTests {

        @Test
        @DisplayName("pageSize > 50 返回参数错误")
        void shouldFailWhenPageSizeTooLarge() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("listpage01", "password123");

            mockMvc.perform(get("/user/list/page")
                            .param("pageSize", "51")
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("按用户昵称分页查询时支持模糊匹配")
        void shouldListUsersByFuzzyUserName() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("listpage02", "password123");
            User targetUser = new User();
            targetUser.setUserName("分页模糊目标用户");
            targetUser.setUserAccount("listpage_like_target");
            targetUser.setUserPassword(userService.encodePassword("password123"));
            targetUser.setUserRole(UserConstant.DEFAULT_ROLE);
            userService.save(targetUser);

            User otherUser = new User();
            otherUser.setUserName("分页普通用户");
            otherUser.setUserAccount("listpage_like_other");
            otherUser.setUserPassword(userService.encodePassword("password123"));
            otherUser.setUserRole(UserConstant.DEFAULT_ROLE);
            userService.save(otherUser);

            mockMvc.perform(get("/user/list/page")
                            .param("userName", "模糊目标")
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.records[0].userAccount").value("listpage_like_target"));
        }

        @Test
        @DisplayName("直接调用分页方法时 pageSize > 50 返回参数错误")
        void shouldRejectOversizedPageWhenCalledDirectly() throws Exception {
            UserQueryRequest queryRequest = new UserQueryRequest();
            queryRequest.setPageSize(51);
            UserController targetController = AopTestUtils.getTargetObject(userController);

            assertThrows(BusinessException.class,
                    () -> targetController.listUserByPage(queryRequest, new MockHttpServletRequest()));
        }
    }

    @Nested
    @DisplayName("POST /user/add 创建用户 - 角色安全")
    class AddUserRoleTests {

        @Test
        @DisplayName("管理员通过 add 接口不能指定角色，新用户始终为普通用户")
        void shouldNotAllowAdminToSetRoleViaAdd() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("addrole01", "password123");

            // 构造请求体，尝试夹带 admin 角色
            String requestBody = "{\"userAccount\":\"newuser01\",\"userPassword\":\"password123\","
                    + "\"userName\":\"新用户\",\"userRole\":\"admin\"}";

            String response = mockMvc.perform(post("/user/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn().getResponse().getContentAsString();

            // 提取新用户 id 并查询，验证角色为普通用户
            Long newUserId = objectMapper.readTree(response).get("data").asLong();
            User newUser = userService.getById(newUserId);
            org.assertj.core.api.Assertions.assertThat(newUser.getUserRole())
                    .as("通过 add 接口创建的用户角色应为默认普通用户")
                    .isEqualTo(UserConstant.DEFAULT_ROLE);
        }
    }

    @Nested
    @DisplayName("POST /user/update 更新用户 - 角色安全")
    class UpdateUserRoleTests {

        @Test
        @DisplayName("管理员通过 update 接口不能修改用户角色")
        void shouldNotAllowAdminToChangeRoleViaUpdate() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("updrole01", "password123");

            // 创建一个普通用户
            long userId = userService.userRegister("updtgt01", "password123", "password123");
            User targetUser = userService.getById(userId);
            org.assertj.core.api.Assertions.assertThat(targetUser.getUserRole())
                    .as("新注册用户角色应为普通用户")
                    .isEqualTo(UserConstant.DEFAULT_ROLE);

            // 构造请求体，尝试夹带 admin 角色
            String requestBody = "{\"id\":" + userId + ",\"userName\":\"修改后的名字\",\"userRole\":\"admin\"}";

            mockMvc.perform(post("/user/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            // 验证角色未被修改
            User updatedUser = userService.getById(userId);
            org.assertj.core.api.Assertions.assertThat(updatedUser.getUserRole())
                    .as("通过 update 接口不应修改用户角色")
                    .isEqualTo(UserConstant.DEFAULT_ROLE);
            // 验证其他字段确实被修改了
            org.assertj.core.api.Assertions.assertThat(updatedUser.getUserName())
                    .as("userName 应被正常更新")
                    .isEqualTo("修改后的名字");
        }
    }

    @Nested
    @DisplayName("POST /user/update/role 用户角色变更")
    class UpdateRoleTests {

        @Test
        @DisplayName("管理员通过专用接口成功修改用户角色")
        void shouldAllowAdminToUpdateRoleViaDedicatedApi() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("roleapi01", "password123");

            // 创建一个普通用户
            long userId = userService.userRegister("roletgt01", "password123", "password123");

            String requestBody = "{\"id\":" + userId + ",\"userRole\":\"admin\"}";

            mockMvc.perform(post("/user/update/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(true));

            // 验证角色已变更
            User updatedUser = userService.getById(userId);
            org.assertj.core.api.Assertions.assertThat(updatedUser.getUserRole())
                    .as("通过专用接口应成功修改用户角色")
                    .isEqualTo(UserConstant.ADMIN_ROLE);
        }

        @Test
        @DisplayName("非法角色值应被拒绝")
        void shouldRejectInvalidRole() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("roleapi02", "password123");

            long userId = userService.userRegister("roletgt02", "password123", "password123");

            String requestBody = "{\"id\":" + userId + ",\"userRole\":\"superadmin\"}";

            mockMvc.perform(post("/user/update/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));

            // 验证角色未被修改
            User user = userService.getById(userId);
            org.assertj.core.api.Assertions.assertThat(user.getUserRole())
                    .as("非法角色值不应修改用户角色")
                    .isEqualTo(UserConstant.DEFAULT_ROLE);
        }

        @Test
        @DisplayName("空角色值应被拒绝")
        void shouldRejectBlankRole() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("roleapi03", "password123");

            long userId = userService.userRegister("roletgt03", "password123", "password123");

            String requestBody = "{\"id\":" + userId + ",\"userRole\":\"\"}";

            mockMvc.perform(post("/user/update/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("角色未变更时返回成功但不产生额外更新")
        void shouldReturnSuccessWhenRoleUnchanged() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("roleapi04", "password123");

            // 获取管理员用户 id
            User adminUser = (User) adminSession.getAttribute(UserConstant.USER_LOGIN_STATE);
            Long operatorId = adminUser.getId();

            long userId = userService.userRegister("roletgt04", "password123", "password123");

            // 将用户提升为 admin
            userService.updateUserRole(userId, UserRoleEnum.ADMIN, operatorId);

            // 再次设置为 admin（角色未变更）
            String requestBody = "{\"id\":" + userId + ",\"userRole\":\"admin\"}";

            mockMvc.perform(post("/user/update/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("不存在的用户 id 应返回错误")
        void shouldReturnErrorWhenUserNotFound() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("roleapi05", "password123");

            String requestBody = "{\"id\":999999,\"userRole\":\"admin\"}";

            mockMvc.perform(post("/user/update/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40400));
        }

        @Test
        @DisplayName("非管理员访问 /user/update/role 应返回无权限")
        void shouldRejectNonAdminAccessToUpdateRole() throws Exception {
            MockHttpSession userSession = registerAndLogin("roleapi06", "password123");

            long userId = userService.userRegister("roletgt06", "password123", "password123");

            String requestBody = "{\"id\":" + userId + ",\"userRole\":\"admin\"}";

            mockMvc.perform(post("/user/update/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(userSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));
        }

        @Test
        @DisplayName("最后一个管理员不能被降权")
        void shouldNotAllowDowngradeLastAdmin() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("roleapi07", "password123");

            // 获取当前管理员用户
            User adminUser = (User) adminSession.getAttribute(UserConstant.USER_LOGIN_STATE);
            Long adminUserId = adminUser.getId();

            // 尝试将自己降级为普通用户
            String requestBody = "{\"id\":" + adminUserId + ",\"userRole\":\"user\"}";

            mockMvc.perform(post("/user/update/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(50001));

            // 验证管理员角色未被修改
            User userAfterAttempt = userService.getById(adminUserId);
            org.assertj.core.api.Assertions.assertThat(userAfterAttempt.getUserRole())
                    .as("最后一个管理员不应被降级")
                    .isEqualTo(UserConstant.ADMIN_ROLE);
        }

        @Test
        @DisplayName("角色变更后审计记录应落库")
        void shouldCreateAuditLogWhenRoleChanged() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("roleapi08", "password123");

            // 获取管理员用户 id
            User adminUser = (User) adminSession.getAttribute(UserConstant.USER_LOGIN_STATE);
            Long operatorId = adminUser.getId();

            // 创建一个普通用户
            long targetUserId = userService.userRegister("roletgt08", "password123", "password123");

            // 记录变更前审计记录数量
            long logCountBefore = userRoleChangeLogMapper.selectCount(null);

            String requestBody = "{\"id\":" + targetUserId + ",\"userRole\":\"admin\"}";

            mockMvc.perform(post("/user/update/role")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(true));

            // 验证审计记录已落库
            long logCountAfter = userRoleChangeLogMapper.selectCount(null);
            org.assertj.core.api.Assertions.assertThat(logCountAfter)
                    .as("角色变更后应新增一条审计记录")
                    .isEqualTo(logCountBefore + 1);

            // 验证审计记录内容正确
            UserRoleChangeLog latestLog = userRoleChangeLogMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserRoleChangeLog>()
                            .orderByDesc("id")
                            .last("LIMIT 1")
            ).get(0);

            org.assertj.core.api.Assertions.assertThat(latestLog.getOperatorId())
                    .as("审计记录操作者 id 应正确")
                    .isEqualTo(operatorId);
            org.assertj.core.api.Assertions.assertThat(latestLog.getTargetUserId())
                    .as("审计记录目标用户 id 应正确")
                    .isEqualTo(targetUserId);
            org.assertj.core.api.Assertions.assertThat(latestLog.getOldRole())
                    .as("审计记录旧角色应为 user")
                    .isEqualTo(UserConstant.DEFAULT_ROLE);
            org.assertj.core.api.Assertions.assertThat(latestLog.getNewRole())
                    .as("审计记录新角色应为 admin")
                    .isEqualTo(UserConstant.ADMIN_ROLE);
        }
    }

    @Nested
    @DisplayName("POST /user/delete 删除用户 - 最后管理员保护")
    class DeleteUserTests {

        @Test
        @DisplayName("最后一个管理员不能被删除")
        void shouldNotAllowDeleteLastAdmin() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("deladm01", "password123");

            // 获取当前管理员用户
            User adminUser = (User) adminSession.getAttribute(UserConstant.USER_LOGIN_STATE);
            Long adminUserId = adminUser.getId();

            // 尝试删除自己（最后一个管理员）
            String requestBody = "{\"id\":" + adminUserId + "}";

            mockMvc.perform(post("/user/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(50001));

            // 验证管理员仍然存在
            User userAfterAttempt = userService.getById(adminUserId);
            org.assertj.core.api.Assertions.assertThat(userAfterAttempt)
                    .as("最后一个管理员不应被删除")
                    .isNotNull();
            org.assertj.core.api.Assertions.assertThat(userAfterAttempt.getUserRole())
                    .as("最后一个管理员角色不应改变")
                    .isEqualTo(UserConstant.ADMIN_ROLE);
        }

        @Test
        @DisplayName("有多个管理员时可以删除其中一个")
        void shouldAllowDeleteAdminWhenMultipleAdminsExist() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("deladm02", "password123");

            // 创建第二个管理员
            long secondAdminId = userService.userRegister("deladm03", "password123", "password123");
            userService.updateUserRole(secondAdminId, UserRoleEnum.ADMIN,
                    ((User) adminSession.getAttribute(UserConstant.USER_LOGIN_STATE)).getId());

            // 删除第二个管理员
            String requestBody = "{\"id\":" + secondAdminId + "}";

            mockMvc.perform(post("/user/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(true));

            // 验证第二个管理员已被删除
            User deletedUser = userService.getById(secondAdminId);
            org.assertj.core.api.Assertions.assertThat(deletedUser)
                    .as("第二个管理员应被删除")
                    .isNull();
        }

        @Test
        @DisplayName("删除普通用户不受最后管理员保护限制")
        void shouldAllowDeleteNormalUser() throws Exception {
            MockHttpSession adminSession = registerAndLoginAdmin("deladm04", "password123");

            // 创建一个普通用户
            long normalUserId = userService.userRegister("delusr01", "password123", "password123");

            // 删除普通用户
            String requestBody = "{\"id\":" + normalUserId + "}";

            mockMvc.perform(post("/user/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(true));

            // 验证普通用户已被删除
            User deletedUser = userService.getById(normalUserId);
            org.assertj.core.api.Assertions.assertThat(deletedUser)
                    .as("普通用户应被删除")
                    .isNull();
        }
    }
}
