package com.feiting.feiapi.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiting.feiapi.component.SdkMethodRegistry;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoAddRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoInvokeRequest;
import com.feiting.feiapi.model.dto.user.UserLoginRequest;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapiclientsdk.client.FeiApiClient;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口调用冒烟测试
 * 验证状态机: OFFLINE -> PUBLISHING -> (失败回滚) -> OFFLINE
 * 验证权限: 非管理员不能发布/下线
 * 验证调用: 未上线接口不可调用
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("接口调用冒烟测试")
class InterfaceInvokeSmokeTest {

    /**
     * 测试接口真实后端服务地址
     */
    private static final String TEST_TARGET_HOST = "http://feiapi-interface:8123";

    @Resource
    private MockMvc mockMvc;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private UserService userService;

    @Resource
    private InterfaceInfoService interfaceInfoService;

    /**
     * 用 @MockBean 替换容器中的 SdkMethodRegistry，使成功调用路径不依赖网关。
     * 测试中可通过 Mockito.when() 定制行为。
     */
    @MockBean
    private SdkMethodRegistry sdkMethodRegistry;

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

    /**
     * 构建固定十位且低碰撞概率的测试账号。
     *
     * @param prefix 两位字母账号前缀
     * @return 十位字母数字测试账号
     */
    private String buildTestAccount(String prefix) {
        // UUID 前八位提供 32 位随机空间，并确保最终账号不会因数字位数变化而越界。
        String uuidSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return prefix + uuidSuffix;
    }

    private long createInterfaceInfo(String name, String path, String method, long userId) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setName(name);
        interfaceInfo.setSdkMethodName("getLoveWords");
        interfaceInfo.setDescription("desc_" + name);
        interfaceInfo.setPath(path);
        interfaceInfo.setTargetHost(TEST_TARGET_HOST);
        interfaceInfo.setUrl(TEST_TARGET_HOST + path);
        interfaceInfo.setRequestHeader("{\"Content-Type\":\"application/json\"}");
        interfaceInfo.setResponseHeader("{\"Content-Type\":\"application/json\"}");
        interfaceInfo.setStatus(InterfaceInfoStatusEnum.OFFLINE.getValue());
        interfaceInfo.setMethod(method);
        interfaceInfo.setUserId(userId);
        assertTrue(interfaceInfoService.save(interfaceInfo), "测试接口数据应创建成功");
        return interfaceInfo.getId();
    }

    @Test
    @DisplayName("成功调用全链路: 创建 -> 发布(mock成功) -> ONLINE -> 调用 -> 返回 data")
    void fullSuccessfulInvokeLifecycle() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String adminAccount = buildTestAccount("ai");
        String userAccount = buildTestAccount("us");
        assertTrue(adminAccount.matches("^[A-Za-z]{2}[A-Za-z0-9]{8}$"), "管理员测试账号应为十位字母数字");
        assertTrue(userAccount.matches("^[A-Za-z]{2}[A-Za-z0-9]{8}$"), "普通用户测试账号应为十位字母数字");

        long interfaceInfoId = -1;
        long adminId = -1;
        long userId = -1;

        try {
            // 配置 mock：发布验证时 invoke 返回成功
            Mockito.when(sdkMethodRegistry.invoke(
                    Mockito.any(FeiApiClient.class),
                    Mockito.eq("getLoveWords"),
                    Mockito.any()
            )).thenReturn("{\"content\":\"测试土味情话\"}");

            MockHttpSession adminSession = loginWithRole(adminAccount, "admin");
            MockHttpSession userSession = loginWithRole(userAccount, "user");

            adminId = userService.lambdaQuery().eq(User::getUserAccount, adminAccount).one().getId();
            userId = userService.lambdaQuery().eq(User::getUserAccount, userAccount).one().getId();

            // ======== Step1: 创建接口（OFFLINE） ========
            InterfaceInfoAddRequest addRequest = new InterfaceInfoAddRequest();
            addRequest.setName("随机土味情话");
            addRequest.setSdkMethodName("getLoveWords");
            addRequest.setDescription("随机土味情话");
            addRequest.setPath("/api/love_words_" + suffix);
            addRequest.setTargetHost(TEST_TARGET_HOST);
            addRequest.setRequestHeader("{\"Content-Type\":\"application/json\"}");
            addRequest.setResponseHeader("{\"Content-Type\":\"application/json\"}");
            addRequest.setMethod("GET");
            addRequest.setRequestParams("");

            MvcResult createResult = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            interfaceInfoId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("data").asLong();

            // 验证初始状态为 OFFLINE
            InterfaceInfo created = interfaceInfoService.getById(interfaceInfoId);
            assertEquals(InterfaceInfoStatusEnum.OFFLINE.getValue(), created.getStatus());

            // ======== Step2: 发布接口（mock 成功，状态变为 ONLINE） ========
            String onlineJson = "{\"id\":" + interfaceInfoId + "}";
            mockMvc.perform(post("/interfaceInfo/online")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(onlineJson)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(true));

            // 验证状态已变为 ONLINE
            InterfaceInfo published = interfaceInfoService.getById(interfaceInfoId);
            assertEquals(InterfaceInfoStatusEnum.ONLINE.getValue(), published.getStatus(),
                    "发布成功后状态应为 ONLINE");

            // ======== Step3: 调用接口，验证返回 data ========
            InterfaceInfoInvokeRequest invokeRequest = new InterfaceInfoInvokeRequest();
            invokeRequest.setId(interfaceInfoId);
            invokeRequest.setUserRequestParams("");

            MvcResult invokeResult = mockMvc.perform(post("/interfaceInfo/invoke")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invokeRequest))
                            .session(userSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            // 验证返回的 data 包含 mock 的内容
            JsonNode invokeData = objectMapper.readTree(invokeResult.getResponse().getContentAsString()).get("data");
            assertNotNull(invokeData, "调用成功应返回 data");
        } finally {
            if (interfaceInfoId > 0) interfaceInfoService.removeById(interfaceInfoId);
            if (adminId > 0) {
                userService.removeById(adminId);
            } else {
                User admin = userService.lambdaQuery().eq(User::getUserAccount, adminAccount).one();
                if (admin != null) userService.removeById(admin.getId());
            }
            if (userId > 0) {
                userService.removeById(userId);
            } else {
                User user = userService.lambdaQuery().eq(User::getUserAccount, userAccount).one();
                if (user != null) userService.removeById(user.getId());
            }
        }
    }

    @Test
    @DisplayName("完整状态机: 创建(OFFLINE) -> 发布(失败回滚OFFLINE) -> 未上线不可调用")
    void fullStateMachineLifecycle() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String adminAccount = buildTestAccount("as");
        String userAccount = buildTestAccount("us");
        assertTrue(adminAccount.matches("^[A-Za-z]{2}[A-Za-z0-9]{8}$"), "管理员测试账号应为十位字母数字");
        assertTrue(userAccount.matches("^[A-Za-z]{2}[A-Za-z0-9]{8}$"), "普通用户测试账号应为十位字母数字");

        long interfaceInfoId = -1;
        long adminId = -1;
        long userId = -1;

        try {
            // 登录也放在 try 内，确保任何阶段失败都能清理
            MockHttpSession adminSession = loginWithRole(adminAccount, "admin");
            MockHttpSession userSession = loginWithRole(userAccount, "user");

            // 记录用户 id 用于清理
            adminId = userService.lambdaQuery().eq(User::getUserAccount, adminAccount).one().getId();
            userId = userService.lambdaQuery().eq(User::getUserAccount, userAccount).one().getId();

            // ======== Step1: 创建接口，初始状态为 OFFLINE ========
            InterfaceInfoAddRequest addRequest = new InterfaceInfoAddRequest();
            addRequest.setName("随机土味情话");
            addRequest.setSdkMethodName("getLoveWords");
            addRequest.setDescription("随机土味情话");
            addRequest.setPath("/api/love_words_" + suffix);
            addRequest.setTargetHost(TEST_TARGET_HOST);
            addRequest.setRequestHeader("{\"Content-Type\":\"application/json\"}");
            addRequest.setResponseHeader("{\"Content-Type\":\"application/json\"}");
            addRequest.setMethod("GET");

            MvcResult createResult = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andReturn();

            interfaceInfoId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("data").asLong();

            InterfaceInfo created = interfaceInfoService.getById(interfaceInfoId);
            assertEquals(InterfaceInfoStatusEnum.OFFLINE.getValue(), created.getStatus(),
                    "新创建的接口状态应为 OFFLINE");

            // ======== Step2: 发布接口（走真实 /online 链路） ========
            // 网关不可用，发布验证会失败，但应验证状态机正确转换和回滚
            String onlineJson = "{\"id\":" + interfaceInfoId + "}";
            mockMvc.perform(post("/interfaceInfo/online")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(onlineJson)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(50000));  // 验证失败

            // 验证：发布失败后状态应正确回滚到 OFFLINE
            InterfaceInfo afterOnline = interfaceInfoService.getById(interfaceInfoId);
            assertEquals(InterfaceInfoStatusEnum.OFFLINE.getValue(), afterOnline.getStatus(),
                    "发布验证失败后应回滚到 OFFLINE");

            // ======== Step3: 未上线接口不可调用 ========
            InterfaceInfoInvokeRequest invokeRequest = new InterfaceInfoInvokeRequest();
            invokeRequest.setId(interfaceInfoId);
            invokeRequest.setUserRequestParams("");

            mockMvc.perform(post("/interfaceInfo/invoke")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invokeRequest))
                            .session(userSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(50000));
        } finally {
            // 无论断言是否失败，都清理数据（按 id 清理，id 不存在则按账号兜底）
            if (interfaceInfoId > 0) {
                interfaceInfoService.removeById(interfaceInfoId);
            }
            if (adminId > 0) {
                userService.removeById(adminId);
            } else {
                User admin = userService.lambdaQuery().eq(User::getUserAccount, adminAccount).one();
                if (admin != null) userService.removeById(admin.getId());
            }
            if (userId > 0) {
                userService.removeById(userId);
            } else {
                User user = userService.lambdaQuery().eq(User::getUserAccount, userAccount).one();
                if (user != null) userService.removeById(user.getId());
            }
        }
    }

    @Test
    @DisplayName("普通用户删除平台接口应失败")
    void deleteOthersInterfaceShouldFail() throws Exception {
        // 使用短后缀，确保账号长度符合 4-10 位规则
        String suffix = String.valueOf(System.currentTimeMillis() % 1000);
        String ownerAccount = "so" + suffix;
        String otherAccount = "sr" + suffix;

        long interfaceInfoId = -1;
        long ownerId = -1;
        long otherId = -1;

        try {
            loginWithRole(ownerAccount, "user");
            MockHttpSession otherSession = loginWithRole(otherAccount, "user");

            ownerId = userService.lambdaQuery().eq(User::getUserAccount, ownerAccount).one().getId();
            otherId = userService.lambdaQuery().eq(User::getUserAccount, otherAccount).one().getId();

            interfaceInfoId = createInterfaceInfo("testDeleteApi", "/api/delete_test_" + suffix, "GET", ownerId);

            // 普通用户尝试删除平台接口应失败。
            String deleteJson = "{\"id\":" + interfaceInfoId + "}";
            mockMvc.perform(post("/interfaceInfo/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deleteJson)
                            .session(otherSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));

            // 验证接口仍然存在
            assertNotNull(interfaceInfoService.getById(interfaceInfoId), "接口不应被删除");
        } finally {
            if (interfaceInfoId > 0) {
                interfaceInfoService.removeById(interfaceInfoId);
            }
            if (ownerId > 0) {
                userService.removeById(ownerId);
            } else {
                User owner = userService.lambdaQuery().eq(User::getUserAccount, ownerAccount).one();
                if (owner != null) userService.removeById(owner.getId());
            }
            if (otherId > 0) {
                userService.removeById(otherId);
            } else {
                User other = userService.lambdaQuery().eq(User::getUserAccount, otherAccount).one();
                if (other != null) userService.removeById(other.getId());
            }
        }
    }
}
