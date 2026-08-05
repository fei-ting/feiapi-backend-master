package com.feiting.feiapi.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.constant.UserConstant;
import com.feiting.feiapi.controller.InterfaceInfoController;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoAddRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoInvokeRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoQueryRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoUpdateRequest;
import com.feiting.feiapi.model.vo.InterfaceInfoVO;
import com.feiting.feiapi.model.dto.user.UserLoginRequest;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDoc;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.interfaceplatform.documentation.model.entity.InterfaceDocParam;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import com.feiting.feiapi.service.InterfaceDocService;
import com.feiting.feiapi.service.InterfaceDocParamService;
import com.feiting.feiapi.service.InterfaceInfoLifecycleService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InterfaceInfoController 集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("InterfaceInfoController 集成测试")
class InterfaceInfoControllerTest {

    /**
     * 测试接口真实后端服务地址
     */
    private static final String TEST_TARGET_HOST = "http://feiapi-interface:8123";

    /**
     * 测试配置中的发布探测管理员 AccessKey。
     */
    private static final String TEST_PROBE_ACCESS_KEY = "test-access-key";

    /**
     * 测试配置中的发布探测管理员 SecretKey。
     */
    private static final String TEST_PROBE_SECRET_KEY = "test-secret-key";

    @Resource
    private MockMvc mockMvc;

    @Resource
    private UserService userService;

    @Resource
    private InterfaceInfoService interfaceInfoService;

    /**
     * 接口文档服务。
     */
    @Resource
    private InterfaceDocService interfaceDocService;

    /**
     * 接口文档参数服务。
     */
    @Resource
    private InterfaceDocParamService interfaceDocParamService;

    /**
     * 接口文档错误码服务。
     */
    @Resource
    private InterfaceDocErrorCodeService interfaceDocErrorCodeService;

    /**
     * 接口信息生命周期服务。
     */
    @Resource
    private InterfaceInfoLifecycleService interfaceInfoLifecycleService;

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private InterfaceInfoController interfaceInfoController;

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
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest))
                        .session(session))
                .andExpect(status().isOk());
        return session;
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        // 使用短前缀 + 时间戳后4位，确保账号长度符合 4-10 位规则
        String timestamp = String.valueOf(System.currentTimeMillis());
        return loginWithRole("aif" + timestamp.substring(timestamp.length() - 4), "admin");
    }

    /**
     * 使用具备发布探测配置密钥的管理员登录。
     *
     * @return 管理员登录会话
     */
    private MockHttpSession loginAsProbeAdmin() throws Exception {
        MockHttpSession session = loginAsAdmin();
        User adminUser = (User) session.getAttribute(UserConstant.USER_LOGIN_STATE);
        adminUser.setAccessKey(TEST_PROBE_ACCESS_KEY);
        adminUser.setSecretKey(TEST_PROBE_SECRET_KEY);
        assertTrue(userService.updateById(adminUser), "发布探测管理员密钥应配置成功");
        session.setAttribute(UserConstant.USER_LOGIN_STATE, adminUser);
        return session;
    }

    private MockHttpSession loginAsUser() throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis());
        return loginWithRole("uif" + timestamp.substring(timestamp.length() - 4), "user");
    }

    private InterfaceInfoAddRequest buildAddRequest(String name, String path, String method) {
        InterfaceInfoAddRequest request = new InterfaceInfoAddRequest();
        request.setName(name);
        request.setSdkMethodName("getLoveWords");
        request.setDescription("desc_" + name);
        request.setPath(path);
        request.setTargetHost(TEST_TARGET_HOST);
        request.setUrl(TEST_TARGET_HOST + path);
        request.setRequestHeader("{\"Content-Type\":\"application/json\"}");
        request.setResponseHeader("{\"Content-Type\":\"application/json\"}");
        request.setMethod(method);
        return request;
    }

    private long createInterfaceInfo(String name, String path, String method, int status) {
        return createInterfaceInfo(name, path, method, status, null);
    }

    /**
     * 创建测试接口信息。
     *
     * @param name          接口名称
     * @param path          接口路径
     * @param method        请求方法
     * @param status        接口状态
     * @param requestParams 请求参数模板
     * @return 接口 id
     */
    private long createInterfaceInfo(String name, String path, String method, int status, String requestParams) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setName(name);
        interfaceInfo.setSdkMethodName("getLoveWords");
        interfaceInfo.setDescription("desc_" + name);
        interfaceInfo.setPath(path);
        interfaceInfo.setTargetHost(TEST_TARGET_HOST);
        interfaceInfo.setUrl(TEST_TARGET_HOST + path);
        interfaceInfo.setRequestParams(requestParams);
        interfaceInfo.setRequestHeader("{\"Content-Type\":\"application/json\"}");
        interfaceInfo.setResponseHeader("{\"Content-Type\":\"application/json\"}");
        interfaceInfo.setStatus(status);
        interfaceInfo.setMethod(method);
        interfaceInfo.setUserId(1L);
        assertTrue(interfaceInfoService.save(interfaceInfo), "测试接口数据应创建成功");
        return interfaceInfo.getId();
    }

    private void insertUserInterfaceInfo(long userId, long interfaceInfoId, int totalNum) {
        UserInterfaceInfo userInterfaceInfo = new UserInterfaceInfo();
        userInterfaceInfo.setUserId(userId);
        userInterfaceInfo.setInterfaceInfoId(interfaceInfoId);
        userInterfaceInfo.setLeftNum(0);
        userInterfaceInfo.setTotalNum(totalNum);
        userInterfaceInfo.setStatus(0);
        assertTrue(userInterfaceInfoService.save(userInterfaceInfo), "测试调用关系应创建成功");
    }

    /**
     * 为测试接口创建指定维护状态的文档主记录。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param docStatus       文档状态
     */
    private void createDocWithStatus(long interfaceInfoId, String docStatus) {
        InterfaceDoc doc = new InterfaceDoc();
        doc.setInterfaceInfoId(interfaceInfoId);
        doc.setDocStatus(docStatus);
        doc.setDocVersion("v1");
        doc.setRequestContentType("application/json");
        doc.setResponseContentType("application/json");
        doc.setSuccessExample("{\"content\":\"ok\"}");
        assertTrue(interfaceDocService.save(doc), "测试文档主记录应创建成功");
    }

    /**
     * 清理非事务回滚测试真实提交的数据。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param adminSession    管理员登录会话
     */
    private void cleanupCommittedRollbackTestData(Long interfaceInfoId, MockHttpSession adminSession) {
        User adminUser = adminSession == null
                ? null
                : (User) adminSession.getAttribute(UserConstant.USER_LOGIN_STATE);
        assertAll("非事务回滚测试数据应清理完整",
                () -> {
                    if (interfaceInfoId != null) {
                        interfaceDocParamService.lambdaUpdate()
                                .eq(InterfaceDocParam::getInterfaceInfoId, interfaceInfoId)
                                .remove();
                    }
                },
                () -> {
                    if (interfaceInfoId != null) {
                        interfaceDocService.lambdaUpdate()
                                .eq(InterfaceDoc::getInterfaceInfoId, interfaceInfoId)
                                .remove();
                    }
                },
                () -> {
                    if (interfaceInfoId != null) {
                        userInterfaceInfoService.lambdaUpdate()
                                .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                                .remove();
                    }
                },
                () -> {
                    if (interfaceInfoId != null) {
                        interfaceInfoService.removeById(interfaceInfoId);
                    }
                },
                () -> {
                    if (adminUser != null && adminUser.getId() != null) {
                        userInterfaceInfoService.lambdaUpdate()
                                .eq(UserInterfaceInfo::getUserId, adminUser.getId())
                                .remove();
                    }
                },
                () -> {
                    if (adminUser != null && adminUser.getId() != null) {
                        userService.removeById(adminUser.getId());
                    }
                });
    }

    /**
     * 创建用于受控配置变更测试的下线接口。
     *
     * @param name 接口名称
     * @return 接口 ID
     */
    private long createControlledConfigInterface(String name) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setName(name);
        interfaceInfo.setSdkMethodName("getControlledConfig");
        interfaceInfo.setDescription("初始公开描述");
        interfaceInfo.setPath("/api/controlled_" + name);
        interfaceInfo.setTargetHost(TEST_TARGET_HOST);
        interfaceInfo.setUrl(TEST_TARGET_HOST + "/api/controlled_" + name);
        interfaceInfo.setRequestParams("{\"original\":\"string\"}");
        interfaceInfo.setRequestHeader("{\"Content-Type\":\"application/json\"}");
        interfaceInfo.setResponseHeader("{\"Content-Type\":\"application/json\"}");
        interfaceInfo.setStatus(InterfaceInfoStatusEnum.OFFLINE.getValue());
        interfaceInfo.setMethod("POST");
        interfaceInfo.setQuotaType("BASIC_QUOTA");
        interfaceInfo.setUserId(1L);
        assertTrue(interfaceInfoService.save(interfaceInfo), "受控配置测试接口应创建成功");
        return interfaceInfo.getId();
    }

    /**
     * 提供九类受控配置的单字段有效变更。
     *
     * @return 用例名称、字段修改操作及是否应同步请求参数文档
     */
    private static Stream<Arguments> controlledConfigChanges() {
        return Stream.of(
                Arguments.of("名称", (Consumer<InterfaceInfo>) item -> item.setName("变更后的接口名称"), false),
                Arguments.of("描述", (Consumer<InterfaceInfo>) item -> item.setDescription("变更后的公开描述"), false),
                Arguments.of("请求方法", (Consumer<InterfaceInfo>) item -> item.setMethod("GET"), true),
                Arguments.of("网关路径", (Consumer<InterfaceInfo>) item -> item.setPath("/api/controlled_changed_path"), false),
                Arguments.of("真实后端地址", (Consumer<InterfaceInfo>) item -> item.setTargetHost("http://changed-service:8123"), false),
                Arguments.of("展示地址", (Consumer<InterfaceInfo>) item -> item.setUrl("http://changed-service:8123/public"), false),
                Arguments.of("配额类型", (Consumer<InterfaceInfo>) item -> item.setQuotaType("ADVANCED_TRIAL"), false),
                Arguments.of("SDK 方法名", (Consumer<InterfaceInfo>) item -> item.setSdkMethodName("getChangedControlledConfig"), false),
                Arguments.of("运行时请求参数模板", (Consumer<InterfaceInfo>) item -> item.setRequestParams("{\"changed\":\"number\"}"), true));
    }

    @Nested
    @DisplayName("POST /interfaceInfo/add 创建接口")
    class AddTests {

        @Test
        @DisplayName("管理员创建接口成功，数据库可查")
        void shouldAddInterfaceInfo() throws Exception {
            MockHttpSession session = loginAsAdmin();
            InterfaceInfoAddRequest request = buildAddRequest("addApi", "/api/add_test", "GET");

            MvcResult result = mockMvc.perform(post("/interfaceInfo/add")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isNumber())
                    .andReturn();

            long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();

            // 验证数据库状态
            InterfaceInfo saved = interfaceInfoService.getById(id);
        assertNotNull(saved);
        assertEquals("addApi", saved.getName());
        assertEquals("getLoveWords", saved.getSdkMethodName());
        assertEquals("/api/add_test", saved.getPath());
        assertEquals(TEST_TARGET_HOST + "/api/add_test", saved.getUrl());
        assertEquals(InterfaceInfoStatusEnum.OFFLINE.getValue(), saved.getStatus());
        InterfaceDoc doc = interfaceDocService.lambdaQuery()
                .eq(InterfaceDoc::getInterfaceInfoId, id)
                .one();
        assertNotNull(doc);
        assertEquals("DRAFT", doc.getDocStatus());
        }

        @Test
        @DisplayName("普通用户创建接口返回无权限")
        void shouldDenyNormalUserAdd() throws Exception {
            MockHttpSession session = loginAsUser();
            InterfaceInfoAddRequest request = buildAddRequest("normalAddApi", "/api/normal_add", "GET");

            mockMvc.perform(post("/interfaceInfo/add")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));
        }

        @Test
        @DisplayName("请求体为空返回参数错误")
        void shouldFailWhenBodyNull() throws Exception {
            MockHttpSession session = loginAsAdmin();

            mockMvc.perform(post("/interfaceInfo/add")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("未登录返回未登录错误")
        void shouldFailWhenNotLoggedIn() throws Exception {
            InterfaceInfoAddRequest request = buildAddRequest("noLoginApi", "/api/nologin", "GET");

            mockMvc.perform(post("/interfaceInfo/add")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40100));
        }

        @Test
        @DisplayName("name 过长返回参数错误")
        void shouldFailWhenNameTooLong() throws Exception {
            MockHttpSession session = loginAsAdmin();
            StringBuilder longName = new StringBuilder();
            for (int i = 0; i < 51; i++) longName.append('a');

            InterfaceInfoAddRequest request = buildAddRequest(longName.toString(), "/api/longname", "GET");

            mockMvc.perform(post("/interfaceInfo/add")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }

    @Nested
    @DisplayName("GET /interfaceInfo/get 获取接口详情")
    class GetByIdTests {

        @Test
        @DisplayName("根据 id 获取接口信息")
        void shouldGetById() throws Exception {
            long id = createInterfaceInfo("getApi", "/api/get_test", "GET", InterfaceInfoStatusEnum.ONLINE.getValue());

            mockMvc.perform(get("/interfaceInfo/get").param("id", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("getApi"))
                .andExpect(jsonPath("$.data.path").value("/api/get_test"))
                .andExpect(jsonPath("$.data.url").value(TEST_TARGET_HOST + "/api/get_test"));
        }

        @Test
        @DisplayName("普通用户查看未上线接口返回数据不存在")
        void shouldHideOfflineInterfaceForNormalUser() throws Exception {
            long id = createInterfaceInfo("hiddenApi", "/api/hidden_get", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());

            mockMvc.perform(get("/interfaceInfo/get").param("id", String.valueOf(id)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40400));
        }

        @Test
        @DisplayName("管理员可以查看未上线接口")
        void shouldAllowAdminGetOfflineInterface() throws Exception {
            MockHttpSession adminSession = loginAsAdmin();
            long id = createInterfaceInfo("adminGetApi", "/api/admin_get", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());

            mockMvc.perform(get("/interfaceInfo/get")
                            .param("id", String.valueOf(id))
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.name").value("adminGetApi"));
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            mockMvc.perform(get("/interfaceInfo/get").param("id", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("不存在的 id 返回数据不存在")
        void shouldReturnNullForNonExistent() throws Exception {
            mockMvc.perform(get("/interfaceInfo/get").param("id", "99999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40400));
        }
    }

    @Nested
    @DisplayName("POST /interfaceInfo/update 更新接口")
    class UpdateTests {

        @Test
        @DisplayName("管理员更新成功，数据库基础信息已变化")
        void shouldUpdateInterfaceByAdmin() throws Exception {
            MockHttpSession session = loginAsAdmin();
            long id = createInterfaceInfo("updateApi", "/api/update_test", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());

            InterfaceInfoUpdateRequest updateRequest = new InterfaceInfoUpdateRequest();
            updateRequest.setId(id);
            updateRequest.setName("updatedApi");
            updateRequest.setSdkMethodName("getUsernameByPost");
            updateRequest.setDescription("已更新");
            updateRequest.setUrl("/api/updated");
            updateRequest.setRequestHeader("{\"Content-Type\":\"application/json\"}");
            updateRequest.setResponseHeader("{\"Content-Type\":\"application/json\"}");
            updateRequest.setMethod("POST");

            mockMvc.perform(post("/interfaceInfo/update")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            // 验证数据库状态已变化
            InterfaceInfo updated = interfaceInfoService.getById(id);
            assertEquals("updatedApi", updated.getName());
            assertEquals("getUsernameByPost", updated.getSdkMethodName());
            assertEquals("/api/updated", updated.getUrl());
            assertEquals("POST", updated.getMethod());
        }

        @Test
        @DisplayName("管理员更新时不能通过通用接口修改状态和归属人")
        void shouldIgnoreStatusAndUserIdWhenUpdate() throws Exception {
            MockHttpSession adminSession = loginAsAdmin();
            long id = createInterfaceInfo("sensitiveApi", "/api/sensitive_update", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());
            InterfaceInfo before = interfaceInfoService.getById(id);

            InterfaceInfoUpdateRequest updateRequest = new InterfaceInfoUpdateRequest();
            updateRequest.setId(id);
            updateRequest.setName("sensitiveUpdated");
            updateRequest.setStatus(InterfaceInfoStatusEnum.ONLINE.getValue());
            updateRequest.setUserId(99999L);

            mockMvc.perform(post("/interfaceInfo/update")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest))
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            InterfaceInfo updated = interfaceInfoService.getById(id);
            assertEquals("sensitiveUpdated", updated.getName());
            assertEquals(before.getStatus(), updated.getStatus());
            assertEquals(before.getUserId(), updated.getUserId());
        }

        @Test
        @DisplayName("普通用户更新接口返回无权限")
        void shouldDenyNormalUserUpdate() throws Exception {
            MockHttpSession userSession = loginAsUser();
            long id = createInterfaceInfo("ownApi", "/api/own_upd", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());
            InterfaceInfoUpdateRequest updateRequest = new InterfaceInfoUpdateRequest();
            updateRequest.setId(id);
            updateRequest.setName("hacked");

            mockMvc.perform(post("/interfaceInfo/update")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest))
                            .session(userSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));

            // 验证未被修改
            InterfaceInfo unchanged = interfaceInfoService.getById(id);
            assertEquals("ownApi", unchanged.getName());
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            MockHttpSession session = loginAsAdmin();

            InterfaceInfoUpdateRequest request = new InterfaceInfoUpdateRequest();
            request.setId(0L);

            mockMvc.perform(post("/interfaceInfo/update")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("记录不存在返回数据不存在")
        void shouldFailWhenNotFound() throws Exception {
            MockHttpSession session = loginAsAdmin();

            InterfaceInfoUpdateRequest request = new InterfaceInfoUpdateRequest();
            request.setId(99999L);
            request.setName("test");

            mockMvc.perform(post("/interfaceInfo/update")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40400));
        }

        /**
         * 在线接口不能通过通用更新入口修改。
         */
        @Test
        @DisplayName("在线接口更新被后端状态门禁拒绝")
        void shouldRejectOnlineInterfaceUpdate() throws Exception {
            MockHttpSession session = loginAsAdmin();
            long id = createInterfaceInfo("onlineUpdateApi", "/api/online_update_gate", "GET",
                    InterfaceInfoStatusEnum.ONLINE.getValue());
            InterfaceInfoUpdateRequest request = new InterfaceInfoUpdateRequest();
            request.setId(id);
            request.setDescription("不应保存");

            mockMvc.perform(post("/interfaceInfo/update")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(50001));
        }

        /**
         * 受控配置发生有效变化后，已完成文档应降为草稿。
         */
        @Test
        @DisplayName("受控配置有效变化后文档降为草稿")
        void shouldDowngradeReadyDocWhenControlledConfigChanges() throws Exception {
            MockHttpSession session = loginAsAdmin();
            long id = createInterfaceInfo("downgradeApi", "/api/downgrade", "GET",
                    InterfaceInfoStatusEnum.OFFLINE.getValue());
            createDocWithStatus(id, "READY");
            InterfaceInfoUpdateRequest request = new InterfaceInfoUpdateRequest();
            request.setId(id);
            request.setDescription("更新后的公开描述");

            mockMvc.perform(post("/interfaceInfo/update")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            InterfaceDoc doc = interfaceDocService.lambdaQuery()
                    .eq(InterfaceDoc::getInterfaceInfoId, id)
                    .one();
            assertEquals("DRAFT", doc.getDocStatus());
        }

        /**
         * 测试九类受控配置任一发生有效变化时，已完成文档均降为草稿；
         * 请求方法和运行时请求参数模板变化还必须同步请求参数文档。
         *
         * @param fieldName             发生变化的受控字段名称
         * @param mutation              单字段修改操作
         * @param shouldSyncRequestDoc 是否应同步请求参数文档
         */
        @ParameterizedTest(name = "{0} 变化后文档降为草稿")
        @MethodSource("com.feiting.feiapi.integration.controller.InterfaceInfoControllerTest#controlledConfigChanges")
        @DisplayName("九类受控配置有效变化后文档降为草稿")
        void shouldDowngradeReadyDocForEveryControlledConfigChange(String fieldName,
                                                                     Consumer<InterfaceInfo> mutation,
                                                                     boolean shouldSyncRequestDoc) {
            long id = createControlledConfigInterface("controlled" + System.nanoTime());
            createDocWithStatus(id, "READY");
            InterfaceInfo updateRequest = new InterfaceInfo();
            updateRequest.setId(id);
            mutation.accept(updateRequest);

            assertTrue(interfaceInfoLifecycleService.updateInterfaceInfoWithDoc(updateRequest),
                    fieldName + "有效变化应更新成功");

            InterfaceDoc doc = interfaceDocService.lambdaQuery()
                    .eq(InterfaceDoc::getInterfaceInfoId, id)
                    .one();
            assertNotNull(doc);
            assertEquals("DRAFT", doc.getDocStatus());
            if (shouldSyncRequestDoc) {
                InterfaceInfo latestInterfaceInfo = interfaceInfoService.getById(id);
                List<InterfaceDocParam> requestParams = interfaceDocParamService.lambdaQuery()
                        .eq(InterfaceDocParam::getInterfaceInfoId, id)
                        .list();
                assertEquals(1, requestParams.size());
                assertEquals(latestInterfaceInfo.getMethod().equals("GET") ? "QUERY" : "BODY",
                        requestParams.get(0).getParamScene());
                assertEquals(latestInterfaceInfo.getRequestParams().contains("changed") ? "changed" : "original",
                        requestParams.get(0).getName());
            }
        }

        /**
         * 标准化后的数据库最终值未变化时，已完成状态应保持不变。
         */
        @Test
        @DisplayName("受控配置同值更新保持已完成状态")
        void shouldKeepReadyDocWhenControlledConfigUnchanged() throws Exception {
            MockHttpSession session = loginAsAdmin();
            long id = createInterfaceInfo("unchangedApi", "/api/unchanged", "GET",
                    InterfaceInfoStatusEnum.OFFLINE.getValue());
            createDocWithStatus(id, "READY");
            InterfaceInfoUpdateRequest request = new InterfaceInfoUpdateRequest();
            request.setId(id);
            request.setName("unchangedApi");

            mockMvc.perform(post("/interfaceInfo/update")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            InterfaceDoc doc = interfaceDocService.lambdaQuery()
                    .eq(InterfaceDoc::getInterfaceInfoId, id)
                    .one();
            assertEquals("READY", doc.getDocStatus());
        }

        /**
         * 只修改非受控旧字段时，不得因更新默认值补齐而误降级文档。
         */
        @Test
        @DisplayName("只修改非受控字段保持已完成状态")
        void shouldKeepReadyDocWhenOnlyUncontrolledFieldChanges() throws Exception {
            MockHttpSession session = loginAsAdmin();
            long id = createInterfaceInfo("uncontrolledApi", "/api/uncontrolled", "GET",
                    InterfaceInfoStatusEnum.OFFLINE.getValue());
            InterfaceInfo interfaceInfo = new InterfaceInfo();
            interfaceInfo.setId(id);
            interfaceInfo.setQuotaType("FREE_UNLIMITED");
            assertTrue(interfaceInfoService.updateById(interfaceInfo));
            createDocWithStatus(id, "READY");
            InterfaceInfoUpdateRequest request = new InterfaceInfoUpdateRequest();
            request.setId(id);
            request.setRequestHeader("旧字段仅用于兼容");

            mockMvc.perform(post("/interfaceInfo/update")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            InterfaceDoc doc = interfaceDocService.lambdaQuery()
                    .eq(InterfaceDoc::getInterfaceInfoId, id)
                    .one();
            assertEquals("FREE_UNLIMITED", interfaceInfoService.getById(id).getQuotaType());
            assertEquals("READY", doc.getDocStatus());
        }

        /**
         * 参数同步超过数量上限时，接口更新、同步和状态变化必须整体回滚。
         */
        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("参数同步超限时接口更新和文档状态整体回滚")
        void shouldRollbackUpdateAndDocStatusWhenRequestSyncExceedsLimit() throws Exception {
            MockHttpSession session = null;
            Long id = null;
            try {
                session = loginAsAdmin();
                id = createInterfaceInfo("rollbackApi", "/api/rollback", "POST",
                        InterfaceInfoStatusEnum.OFFLINE.getValue(), "{\"original\":\"string\"}");
                createDocWithStatus(id, "READY");
                String oversizedTemplate = IntStream.rangeClosed(1, 101)
                        .mapToObj(index -> "\"field" + index + "\":\"string\"")
                        .collect(Collectors.joining(",", "{", "}"));
                InterfaceInfoUpdateRequest request = new InterfaceInfoUpdateRequest();
                request.setId(id);
                request.setName("shouldRollback");
                request.setRequestParams(oversizedTemplate);

                mockMvc.perform(post("/interfaceInfo/update")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .session(session))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(40000));

                InterfaceInfo interfaceInfo = interfaceInfoService.getById(id);
                InterfaceDoc doc = interfaceDocService.lambdaQuery()
                        .eq(InterfaceDoc::getInterfaceInfoId, id)
                        .one();
                assertEquals("rollbackApi", interfaceInfo.getName());
                assertEquals("{\"original\":\"string\"}", interfaceInfo.getRequestParams());
                assertEquals("READY", doc.getDocStatus());
            } finally {
                cleanupCommittedRollbackTestData(id, session);
            }
        }

        /**
         * 非法文档状态属于一致性异常，受控配置更新不得借机静默修复状态。
         */
        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("非法文档状态下受控配置更新整体回滚")
        void shouldRollbackControlledUpdateWhenPersistedDocStatusIllegal() throws Exception {
            MockHttpSession session = null;
            Long id = null;
            try {
                session = loginAsAdmin();
                id = createInterfaceInfo("illegalRollbackApi", "/api/illegal_rollback", "GET",
                        InterfaceInfoStatusEnum.OFFLINE.getValue());
                createDocWithStatus(id, "BROKEN");
                InterfaceInfoUpdateRequest request = new InterfaceInfoUpdateRequest();
                request.setId(id);
                request.setDescription("不应保存的描述");

                mockMvc.perform(post("/interfaceInfo/update")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .session(session))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(50000));

                assertEquals("desc_illegalRollbackApi", interfaceInfoService.getById(id).getDescription());
                InterfaceDoc doc = interfaceDocService.lambdaQuery()
                        .eq(InterfaceDoc::getInterfaceInfoId, id)
                        .one();
                assertEquals("BROKEN", doc.getDocStatus());
            } finally {
                cleanupCommittedRollbackTestData(id, session);
            }
        }
    }

    @Nested
    @DisplayName("POST /interfaceInfo/delete 删除接口")
    class DeleteTests {

        @Test
        @DisplayName("普通用户删除接口返回无权限")
        void shouldDenyNormalUserDelete() throws Exception {
            MockHttpSession session = loginAsUser();
            long id = createInterfaceInfo("deleteApi", "/api/delete_test", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());

            String deleteJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/interfaceInfo/delete")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deleteJson)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));

            assertNotNull(interfaceInfoService.getById(id));
        }

        @Test
        @DisplayName("管理员删除接口成功并同步逻辑删除文档数据")
        void shouldAllowAdminToDelete() throws Exception {
            String timestamp = String.valueOf(System.currentTimeMillis());
            MockHttpSession adminSession = loginWithRole("adl" + timestamp.substring(timestamp.length() - 4), "admin");
            long id = createInterfaceInfo("adminDelApi", "/api/admin_del", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());
            createDocWithStatus(id, "READY");
            InterfaceDocParam requestParam = buildDocParam(id, "QUERY", "keyword", 1);
            InterfaceDocParam responseParam = buildDocParam(id, "RESPONSE", "data", 2);
            assertTrue(interfaceDocParamService.save(requestParam));
            assertTrue(interfaceDocParamService.save(responseParam));
            InterfaceDocErrorCode errorCode = new InterfaceDocErrorCode();
            errorCode.setInterfaceInfoId(id);
            errorCode.setErrorCode("USER_NOT_FOUND");
            errorCode.setErrorMessage("用户不存在");
            errorCode.setSortOrder(1);
            assertTrue(interfaceDocErrorCodeService.save(errorCode));

            String deleteJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/interfaceInfo/delete")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deleteJson)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            assertNull(interfaceInfoService.getById(id));
            assertNull(interfaceDocService.lambdaQuery().eq(InterfaceDoc::getInterfaceInfoId, id).one());
            assertEquals(0, interfaceDocParamService.lambdaQuery()
                    .eq(InterfaceDocParam::getInterfaceInfoId, id)
                    .count());
            assertEquals(0, interfaceDocErrorCodeService.lambdaQuery()
                    .eq(InterfaceDocErrorCode::getInterfaceInfoId, id)
                    .count());
            assertEquals(id, queryDeletedFlag("interface_info", id));
            assertEquals(queryDeletedIdByInterfaceInfoId("interface_doc", id),
                    queryDeletedFlag("interface_doc", queryDeletedIdByInterfaceInfoId("interface_doc", id)));
            assertEquals(2L, countSelfDeletedRows("interface_doc_param", id));
            assertEquals(1L, countSelfDeletedRows("interface_doc_error_code", id));
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            MockHttpSession session = loginAsAdmin();
            String deleteJson = "{\"id\":0}";

            mockMvc.perform(post("/interfaceInfo/delete")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deleteJson)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        /**
         * 在线接口不能通过删除入口删除。
         */
        @Test
        @DisplayName("在线接口删除被后端状态门禁拒绝")
        void shouldRejectOnlineInterfaceDelete() throws Exception {
            MockHttpSession session = loginAsAdmin();
            long id = createInterfaceInfo("onlineDeleteApi", "/api/online_delete_gate", "GET",
                    InterfaceInfoStatusEnum.ONLINE.getValue());

            mockMvc.perform(post("/interfaceInfo/delete")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":" + id + "}")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(50001))
                    .andExpect(jsonPath("$.message").value("请先下线接口后再删除"));
        }

        @Test
        @DisplayName("发布验证中的接口删除被拒绝并提示发布中")
        void shouldRejectPublishingInterfaceDelete() throws Exception {
            MockHttpSession session = loginAsAdmin();
            long id = createInterfaceInfo("publishingDeleteApi", "/api/publishing_delete_gate", "GET",
                    InterfaceInfoStatusEnum.PUBLISHING.getValue());

            mockMvc.perform(post("/interfaceInfo/delete")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":" + id + "}")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(50001))
                    .andExpect(jsonPath("$.message").value("接口正在发布验证中，不能删除"));
        }

        @Test
        @DisplayName("相同路径和方法可反复创建删除")
        void shouldCreateDeleteAndRecreateSamePathAndMethod() throws Exception {
            MockHttpSession session = loginAsAdmin();
            String path = "/api/recreate_delete";
            long firstId = createInterfaceInfo("firstDeleteApi", path, "POST", InterfaceInfoStatusEnum.OFFLINE.getValue());
            deleteByAdmin(session, firstId);

            long secondId = createInterfaceInfo("secondDeleteApi", path, "POST", InterfaceInfoStatusEnum.OFFLINE.getValue());
            deleteByAdmin(session, secondId);

            long thirdId = createInterfaceInfo("thirdDeleteApi", path, "POST", InterfaceInfoStatusEnum.OFFLINE.getValue());

            assertNotEquals(firstId, secondId);
            assertNotEquals(secondId, thirdId);
            assertEquals(firstId, queryDeletedFlag("interface_info", firstId));
            assertEquals(secondId, queryDeletedFlag("interface_info", secondId));
            assertNotNull(interfaceInfoService.getById(thirdId));
        }

        @Test
        @DisplayName("重复删除已删除接口返回数据不存在")
        void shouldReturnNotFoundWhenDeleteAgain() throws Exception {
            MockHttpSession session = loginAsAdmin();
            long id = createInterfaceInfo("deleteAgainApi", "/api/delete_again", "GET",
                    InterfaceInfoStatusEnum.OFFLINE.getValue());
            deleteByAdmin(session, id);

            mockMvc.perform(post("/interfaceInfo/delete")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":" + id + "}")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40400));
        }
    }

    /**
     * 构建测试用文档参数。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param scene           参数场景
     * @param name            参数名称
     * @param sortOrder       排序值
     * @return 文档参数实体
     */
    private InterfaceDocParam buildDocParam(long interfaceInfoId, String scene, String name, int sortOrder) {
        InterfaceDocParam param = new InterfaceDocParam();
        param.setInterfaceInfoId(interfaceInfoId);
        param.setParamScene(scene);
        param.setName(name);
        param.setType("string");
        param.setRequired(1);
        param.setNullable(0);
        param.setDescription("公开说明");
        param.setSortOrder(sortOrder);
        return param;
    }

    /**
     * 管理员删除指定接口。
     *
     * @param session 管理员会话
     * @param id      接口 ID
     */
    private void deleteByAdmin(MockHttpSession session, long id) throws Exception {
        mockMvc.perform(post("/interfaceInfo/delete")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + "}")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /**
     * 查询逻辑删除标识。
     *
     * @param tableName 表名
     * @param id        主键 ID
     * @return 逻辑删除标识
     */
    private Long queryDeletedFlag(String tableName, Long id) {
        return jdbcTemplate.queryForObject(
                "select is_delete from " + tableName + " where id = ?",
                Long.class,
                id
        );
    }

    /**
     * 按接口 ID 查询已删除文档关联记录 ID。
     *
     * @param tableName       表名
     * @param interfaceInfoId 接口信息 ID
     * @return 已删除记录 ID
     */
    private Long queryDeletedIdByInterfaceInfoId(String tableName, Long interfaceInfoId) {
        return jdbcTemplate.queryForObject(
                "select id from " + tableName + " where interface_info_id = ? and is_delete <> 0",
                Long.class,
                interfaceInfoId
        );
    }

    /**
     * 统计逻辑删除标识等于自身 ID 的关联记录数量。
     *
     * @param tableName       表名
     * @param interfaceInfoId 接口信息 ID
     * @return 记录数量
     */
    private Long countSelfDeletedRows(String tableName, Long interfaceInfoId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where interface_info_id = ? and is_delete = id",
                Long.class,
                interfaceInfoId
        );
    }

    @Nested
    @DisplayName("GET /interfaceInfo/list/page 分页查询")
    class ListPageTests {

        @Test
        @DisplayName("分页查询返回正确数据")
        void shouldReturnPaginatedData() throws Exception {
            MockHttpSession session = loginAsAdmin();

            MvcResult result = mockMvc.perform(get("/interfaceInfo/list/page")
                            .param("current", "1")
                            .param("pageSize", "10")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            // 验证返回了分页结构
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
            com.fasterxml.jackson.databind.JsonNode data = root.get("data");
            assertNotNull(data.get("records"), "应包含 records 字段");
            assertNotNull(data.get("total"), "应包含 total 字段");
            assertNotNull(data.get("current"), "应包含 current 字段");
            assertTrue(StreamSupport.stream(data.get("records").spliterator(), false)
                    .allMatch(record -> record.hasNonNull("docStatus")), "普通分页应返回非空文档状态");
        }

        @Test
        @DisplayName("分页查询返回接口调用总数汇总")
        void shouldReturnTotalNumSummary() throws Exception {
            MockHttpSession session = loginAsAdmin();
            long calledInterfaceId = createInterfaceInfo("totalNumApi", "/api/total_num", "GET", InterfaceInfoStatusEnum.ONLINE.getValue());
            long notCalledInterfaceId = createInterfaceInfo("zeroTotalNumApi", "/api/zero_total_num", "GET", InterfaceInfoStatusEnum.ONLINE.getValue());
            insertUserInterfaceInfo(10001L, calledInterfaceId, 7);
            insertUserInterfaceInfo(10002L, calledInterfaceId, 5);

            MvcResult result = mockMvc.perform(get("/interfaceInfo/list/page")
                            .param("current", "1")
                            .param("pageSize", "50")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            com.fasterxml.jackson.databind.JsonNode records = objectMapper
                    .readTree(result.getResponse().getContentAsString())
                    .get("data")
                    .get("records");
            com.fasterxml.jackson.databind.JsonNode calledInterface = StreamSupport.stream(records.spliterator(), false)
                    .filter(record -> record.get("id").asLong() == calledInterfaceId)
                    .findFirst()
                    .orElse(null);
            com.fasterxml.jackson.databind.JsonNode notCalledInterface = StreamSupport.stream(records.spliterator(), false)
                    .filter(record -> record.get("id").asLong() == notCalledInterfaceId)
                    .findFirst()
                    .orElse(null);

            assertNotNull(calledInterface, "应返回有调用记录的接口");
            assertEquals(12, calledInterface.get("totalNum").asInt(), "调用总数应按接口汇总所有用户记录");
            assertNotNull(notCalledInterface, "应返回无调用记录的接口");
            assertEquals(0, notCalledInterface.get("totalNum").asInt(), "没有调用记录时调用总数应返回 0");
        }

        @Test
        @DisplayName("支持按接口调用总数升序和降序排序")
        void shouldSortByTotalNum() throws Exception {
            MockHttpSession session = loginAsAdmin();
            long lowInterfaceId = createInterfaceInfo("sortTotalLow", "/api/sort_total_low", "GET", InterfaceInfoStatusEnum.ONLINE.getValue());
            long midInterfaceId = createInterfaceInfo("sortTotalMid", "/api/sort_total_mid", "GET", InterfaceInfoStatusEnum.ONLINE.getValue());
            long highInterfaceId = createInterfaceInfo("sortTotalHigh", "/api/sort_total_high", "GET", InterfaceInfoStatusEnum.ONLINE.getValue());
            insertUserInterfaceInfo(10003L, midInterfaceId, 5);
            insertUserInterfaceInfo(10004L, highInterfaceId, 12);
            insertUserInterfaceInfo(10005L, highInterfaceId, 8);

            List<Long> descendIds = queryTotalNumSortedIds(session, "descend");
            List<Long> ascendIds = queryTotalNumSortedIds(session, "ascend");

            assertEquals(Arrays.asList(highInterfaceId, midInterfaceId, lowInterfaceId), descendIds, "调用总数降序排序应正确");
            assertEquals(Arrays.asList(lowInterfaceId, midInterfaceId, highInterfaceId), ascendIds, "调用总数升序排序应正确");

            MvcResult result = mockMvc.perform(get("/interfaceInfo/list/page")
                            .param("current", "1")
                            .param("pageSize", "10")
                            .param("description", "sortTotal")
                            .param("sortField", "totalNum")
                            .param("sortOrder", "descend")
                            .session(session))
                    .andExpect(status().isOk())
                    .andReturn();
            com.fasterxml.jackson.databind.JsonNode records = objectMapper
                    .readTree(result.getResponse().getContentAsString())
                    .get("data")
                    .get("records");
            assertTrue(StreamSupport.stream(records.spliterator(), false)
                    .allMatch(record -> "DRAFT".equals(record.get("docStatus").asText())),
                    "调用总数排序分页缺少文档主记录时应返回 DRAFT");
        }

        /**
         * 查询按调用总数排序后的接口 ID 列表。
         *
         * @param session   管理员会话
         * @param sortOrder 排序方向
         * @return 接口 ID 列表
         */
        private List<Long> queryTotalNumSortedIds(MockHttpSession session, String sortOrder) throws Exception {
            MvcResult result = mockMvc.perform(get("/interfaceInfo/list/page")
                            .param("current", "1")
                            .param("pageSize", "10")
                            .param("description", "sortTotal")
                            .param("sortField", "totalNum")
                            .param("sortOrder", sortOrder)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            com.fasterxml.jackson.databind.JsonNode records = objectMapper
                    .readTree(result.getResponse().getContentAsString())
                    .get("data")
                    .get("records");
            return StreamSupport.stream(records.spliterator(), false)
                    .map(record -> record.get("id").asLong())
                    .collect(Collectors.toList());
        }

        @Test
        @DisplayName("pageSize > 50 返回参数错误")
        void shouldFailWhenPageSizeTooLarge() throws Exception {
            mockMvc.perform(get("/interfaceInfo/list/page")
                            .param("pageSize", "51"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("description 模糊搜索")
        void shouldSupportFuzzySearch() throws Exception {
            MockHttpSession session = loginAsAdmin();

            mockMvc.perform(get("/interfaceInfo/list/page")
                            .param("description", "test")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("sortOrder 为 null 时分页查询不触发空指针")
        void shouldNotThrowWhenSortOrderNull() throws Exception {
            InterfaceInfoQueryRequest queryRequest = new InterfaceInfoQueryRequest();
            queryRequest.setSortField("createTime");
            queryRequest.setSortOrder(null);
            MockHttpServletRequest servletRequest = new MockHttpServletRequest();

            InterfaceInfoController targetController = AopTestUtils.getTargetObject(interfaceInfoController);
            BaseResponse<Page<InterfaceInfoVO>> response = targetController.listInterfaceInfoByPage(queryRequest, servletRequest);

            assertEquals(0, response.getCode());
            assertNotNull(response.getData());
        }

        @Test
        @DisplayName("普通用户分页查询只返回已上线接口")
        void shouldOnlyReturnOnlineInterfacesForNormalUser() throws Exception {
            MockHttpSession session = loginAsUser();
            String onlineName = "onlineListApi";
            String offlineName = "offlineListApi";
            createInterfaceInfo(onlineName, "/api/online_list", "GET", InterfaceInfoStatusEnum.ONLINE.getValue());
            createInterfaceInfo(offlineName, "/api/offline_list", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());

            MvcResult result = mockMvc.perform(get("/interfaceInfo/list/page")
                            .param("current", "1")
                            .param("pageSize", "50")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            com.fasterxml.jackson.databind.JsonNode records = objectMapper
                    .readTree(result.getResponse().getContentAsString())
                    .get("data")
                    .get("records");
            boolean containsOnline = StreamSupport.stream(records.spliterator(), false)
                    .anyMatch(record -> onlineName.equals(record.get("name").asText()));
            boolean containsOffline = StreamSupport.stream(records.spliterator(), false)
                    .anyMatch(record -> offlineName.equals(record.get("name").asText()));

            assertTrue(containsOnline, "普通用户应能看到已上线接口");
            assertFalse(containsOffline, "普通用户不应看到未上线接口");
        }
    }

    @Nested
    @DisplayName("POST /interfaceInfo/online 发布接口")
    class OnlineTests {

        @Test
        @DisplayName("管理员发布 OFFLINE 接口，状态变为 PUBLISHING（验证会失败回滚到 OFFLINE）")
        void shouldStartPublishingFromOffline() throws Exception {
            MockHttpSession adminSession = loginAsProbeAdmin();
            long id = createInterfaceInfo("onlineApi", "/api/online_test", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());
            createDocWithStatus(id, "READY");

            // 发布（会因网关不可用而失败，但应验证状态机转换）
            String onlineJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/interfaceInfo/online")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(onlineJson)
                            .session(adminSession))
                    .andExpect(status().isOk());

            // 验证：发布失败后应回滚到 OFFLINE
            InterfaceInfo after = interfaceInfoService.getById(id);
            assertEquals(InterfaceInfoStatusEnum.OFFLINE.getValue(), after.getStatus(),
                    "发布验证失败后应回滚到 OFFLINE");
        }

        @Test
        @DisplayName("管理员不能发布文档待完善的下线接口")
        void shouldRejectPublishingDraftInterface() throws Exception {
            MockHttpSession adminSession = loginAsProbeAdmin();
            long id = createInterfaceInfo("draftOnlineApi", "/api/draft_online_test", "GET",
                    InterfaceInfoStatusEnum.OFFLINE.getValue());

            mockMvc.perform(post("/interfaceInfo/online")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":" + id + "}")
                    .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40901))
                    .andExpect(jsonPath("$.message").value("接口发布前检查未通过，请先修复检查问题"));
        }

        /**
         * 发布前应重新校验持久化 JSON 示例的 UTF-8 字节边界。
         */
        @Test
        @DisplayName("发布前拒绝持久化响应示例超过 65535 字节")
        void shouldRejectPublishingPersistedOversizedExample() throws Exception {
            MockHttpSession adminSession = loginAsProbeAdmin();
            long id = createInterfaceInfo("oversizedDocOnlineApi", "/api/oversized_doc_online", "GET",
                    InterfaceInfoStatusEnum.OFFLINE.getValue());
            createDocWithStatus(id, "READY");
            InterfaceDoc doc = interfaceDocService.lambdaQuery()
                    .eq(InterfaceDoc::getInterfaceInfoId, id)
                    .one();
            doc.setSuccessExample("{\"content\":\"" + "a".repeat(65536) + "\"}");
            assertTrue(interfaceDocService.updateById(doc));

            mockMvc.perform(post("/interfaceInfo/online")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":" + id + "}")
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40901))
                    .andExpect(jsonPath("$.message")
                            .value("接口发布前检查未通过，请先修复检查问题"));

            assertEquals(InterfaceInfoStatusEnum.OFFLINE.getValue(), interfaceInfoService.getById(id).getStatus());
        }

        @Test
        @DisplayName("管理员不能发布持久化状态非法的接口")
        void shouldRejectPublishingIllegalDocStatus() throws Exception {
            MockHttpSession adminSession = loginAsProbeAdmin();
            long id = createInterfaceInfo("illegalStatusOnlineApi", "/api/illegal_status_online", "GET",
                    InterfaceInfoStatusEnum.OFFLINE.getValue());
            createDocWithStatus(id, "BROKEN");

            mockMvc.perform(post("/interfaceInfo/online")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":" + id + "}")
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40901))
                    .andExpect(jsonPath("$.message").value("接口发布前检查未通过，请先修复检查问题"));
        }

        @Test
        @DisplayName("非管理员发布应返回无权限")
        void shouldDenyNonAdmin() throws Exception {
            String timestamp = String.valueOf(System.currentTimeMillis());
            MockHttpSession userSession = loginWithRole("uon" + timestamp.substring(timestamp.length() - 4), "user");
            long id = createInterfaceInfo("onlineApi2", "/api/online_test2", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());

            String onlineJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/interfaceInfo/online")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(onlineJson)
                            .session(userSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));
        }

        @Test
        @DisplayName("发布不存在的接口返回数据不存在")
        void shouldFailWhenNotFound() throws Exception {
            MockHttpSession adminSession = loginAsAdmin();

            String onlineJson = "{\"id\":99999}";
            mockMvc.perform(post("/interfaceInfo/online")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(onlineJson)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40400));
        }

        @Test
        @DisplayName("发布已上线的接口应失败")
        void shouldFailWhenAlreadyOnline() throws Exception {
            MockHttpSession adminSession = loginAsAdmin();
            long id = createInterfaceInfo("onlineApi3", "/api/online_test3", "GET", InterfaceInfoStatusEnum.ONLINE.getValue());

            // 尝试再次发布
            String onlineJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/interfaceInfo/online")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(onlineJson)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(50001));
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            MockHttpSession adminSession = loginAsAdmin();

            String onlineJson = "{\"id\":0}";
            mockMvc.perform(post("/interfaceInfo/online")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(onlineJson)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }

    @Nested
    @DisplayName("POST /interfaceInfo/offline 下线接口")
    class OfflineTests {

        @Test
        @DisplayName("管理员下线 ONLINE 接口成功")
        void shouldOfflineOnlineInterface() throws Exception {
            MockHttpSession adminSession = loginAsAdmin();
            long id = createInterfaceInfo("offlineApi", "/api/offline_test", "GET", InterfaceInfoStatusEnum.ONLINE.getValue());

            // 下线
            String offlineJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/interfaceInfo/offline")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(offlineJson)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            // 验证数据库状态
            InterfaceInfo after = interfaceInfoService.getById(id);
            assertEquals(InterfaceInfoStatusEnum.OFFLINE.getValue(), after.getStatus());
        }

        @Test
        @DisplayName("非管理员下线应返回无权限")
        void shouldDenyNonAdmin() throws Exception {
            String timestamp = String.valueOf(System.currentTimeMillis());
            MockHttpSession userSession = loginWithRole("uof" + timestamp.substring(timestamp.length() - 4), "user");
            long id = createInterfaceInfo("offlineApi2", "/api/offline_test2", "GET", InterfaceInfoStatusEnum.ONLINE.getValue());

            // 非管理员尝试下线
            String offlineJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/interfaceInfo/offline")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(offlineJson)
                            .session(userSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));
        }

        @Test
        @DisplayName("下线 OFFLINE 接口应失败")
        void shouldFailWhenAlreadyOffline() throws Exception {
            MockHttpSession adminSession = loginAsAdmin();
            long id = createInterfaceInfo("offlineApi3", "/api/offline_test3", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());

            // 接口是 OFFLINE 状态，尝试下线
            String offlineJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/interfaceInfo/offline")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(offlineJson)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(50001));
        }

        @Test
        @DisplayName("下线不存在的接口返回数据不存在")
        void shouldFailWhenNotFound() throws Exception {
            MockHttpSession adminSession = loginAsAdmin();

            String offlineJson = "{\"id\":99999}";
            mockMvc.perform(post("/interfaceInfo/offline")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(offlineJson)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40400));
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            MockHttpSession adminSession = loginAsAdmin();

            String offlineJson = "{\"id\":0}";
            mockMvc.perform(post("/interfaceInfo/offline")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(offlineJson)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }

    @Nested
    @DisplayName("POST /interfaceInfo/invoke 调用接口")
    class InvokeTests {

        /**
         * 用户请求参数超过 65,535 个 UTF-8 字节时返回 HTTP 413。
         */
        @Test
        @DisplayName("用户请求参数超过 65535 字节返回 HTTP 413")
        void shouldRejectInvokeBodyExceedingUtf8ByteLimit() throws Exception {
            MockHttpSession session = loginAsUser();
            InterfaceInfoInvokeRequest request = new InterfaceInfoInvokeRequest();
            request.setId(1L);
            request.setUserRequestParams("中".repeat(21846));

            mockMvc.perform(post("/interfaceInfo/invoke")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(jsonPath("$.code").value(41300))
                    .andExpect(jsonPath("$.message")
                            .value("请求体不能超过 65535 字节"));
        }

        @Test
        @DisplayName("接口不存在返回数据不存在")
        void shouldFailWhenInterfaceNotFound() throws Exception {
            MockHttpSession session = loginAsUser();

            InterfaceInfoInvokeRequest request = new InterfaceInfoInvokeRequest();
            request.setId(99999L);
            request.setUserRequestParams("{\"name\":\"test\"}");

            mockMvc.perform(post("/interfaceInfo/invoke")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40400));
        }

        @Test
        @DisplayName("未上线接口不可调用")
        void shouldFailWhenOffline() throws Exception {
            MockHttpSession session = loginAsUser();
            long id = createInterfaceInfo("offlineInvokeApi", "/api/offline_invoke", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());

            InterfaceInfoInvokeRequest invokeRequest = new InterfaceInfoInvokeRequest();
            invokeRequest.setId(id);
            invokeRequest.setUserRequestParams("{\"name\":\"test\"}");

            mockMvc.perform(post("/interfaceInfo/invoke")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invokeRequest))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(50000));
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            MockHttpSession session = loginAsUser();

            InterfaceInfoInvokeRequest request = new InterfaceInfoInvokeRequest();
            request.setId(0L);

            mockMvc.perform(post("/interfaceInfo/invoke")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("用户请求参数不是合法 JSON 时返回参数错误")
        void shouldFailWhenUserRequestParamsInvalidJson() throws Exception {
            MockHttpSession session = loginAsUser();
            long id = createInterfaceInfo("invalidJsonInvokeApi", "/api/invalid_json_invoke", "GET",
                    InterfaceInfoStatusEnum.ONLINE.getValue());

            InterfaceInfoInvokeRequest request = new InterfaceInfoInvokeRequest();
            request.setId(id);
            request.setUserRequestParams("{\"name\":\"test\"");

            mockMvc.perform(post("/interfaceInfo/invoke")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("请求参数必须是合法 JSON"));
        }

        @Test
        @DisplayName("用户请求参数缺少接口模板字段时返回参数错误")
        void shouldFailWhenUserRequestParamsMissingTemplateField() throws Exception {
            MockHttpSession session = loginAsUser();
            long id = createInterfaceInfo("missingFieldInvokeApi", "/api/missing_field_invoke", "POST",
                    InterfaceInfoStatusEnum.ONLINE.getValue(), "{\"username\":\"string\"}");

            InterfaceInfoInvokeRequest request = new InterfaceInfoInvokeRequest();
            request.setId(id);
            request.setUserRequestParams("{\"ip\":\"8.8.8.8\"}");

            mockMvc.perform(post("/interfaceInfo/invoke")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("请求参数缺少必填字段：username"));
        }

        @Test
        @DisplayName("用户请求参数字段类型不符合接口模板时返回参数错误")
        void shouldFailWhenUserRequestParamsFieldTypeMismatch() throws Exception {
            MockHttpSession session = loginAsUser();
            long id = createInterfaceInfo("typeMismatchInvokeApi", "/api/type_mismatch_invoke", "POST",
                    InterfaceInfoStatusEnum.ONLINE.getValue(), "{\"ip\":\"string\"}");

            InterfaceInfoInvokeRequest request = new InterfaceInfoInvokeRequest();
            request.setId(id);
            request.setUserRequestParams("{\"ip\":123}");

            mockMvc.perform(post("/interfaceInfo/invoke")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("请求参数字段类型错误：ip 应为 string"));
        }

        @Test
        @DisplayName("接口模板要求参数但用户请求参数为空时返回参数错误")
        void shouldFailWhenUserRequestParamsEmptyButTemplateRequiresFields() throws Exception {
            MockHttpSession session = loginAsUser();
            long id = createInterfaceInfo("emptyParamsInvokeApi", "/api/empty_params_invoke", "POST",
                    InterfaceInfoStatusEnum.ONLINE.getValue(), "{\"username\":\"string\"}");

            InterfaceInfoInvokeRequest request = new InterfaceInfoInvokeRequest();
            request.setId(id);
            request.setUserRequestParams("");

            mockMvc.perform(post("/interfaceInfo/invoke")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("请求参数缺少必填字段：username"));
        }
    }
}
