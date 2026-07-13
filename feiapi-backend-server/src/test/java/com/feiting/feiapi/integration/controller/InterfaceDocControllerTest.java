package com.feiting.feiapi.integration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiting.feiapi.model.dto.user.UserLoginRequest;
import com.feiting.feiapi.model.entity.InterfaceDoc;
import com.feiting.feiapi.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.model.entity.InterfaceDocParam;
import com.feiting.feiapi.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import com.feiting.feiapi.service.InterfaceDocParamService;
import com.feiting.feiapi.service.InterfaceDocService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口文档查询控制器集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("InterfaceDocController 集成测试")
class InterfaceDocControllerTest {

    /**
     * 测试接口真实后端服务地址。
     */
    private static final String TEST_TARGET_HOST = "http://feiapi-interface:8123";

    /**
     * MockMvc 测试客户端。
     */
    @Resource
    private MockMvc mockMvc;

    /**
     * JSON 序列化工具。
     */
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 用户服务。
     */
    @Resource
    private UserService userService;

    /**
     * 接口信息服务。
     */
    @Resource
    private InterfaceInfoService interfaceInfoService;

    /**
     * 接口文档主信息服务。
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

    @Test
    @DisplayName("上线接口没有结构化文档时使用旧字段兜底，普通用户不返回 targetHost")
    void shouldFallbackToLegacyFieldsWhenStructuredDocMissing() throws Exception {
        long id = createInterfaceInfo("legacyDocApi", "/api/legacy_doc", InterfaceInfoStatusEnum.ONLINE.getValue());

        JsonNode data = requestDoc(id, null)
                .get("data");

        assertThat(data.get("legacyFallback").asBoolean()).isTrue();
        assertThat(data.get("gatewayUrl").asText()).isEqualTo("http://localhost:8090/api/legacy_doc");
        assertThat(data.get("interfaceInfo").get("targetHost").isNull()).isTrue();
        assertThat(data.get("requestHeaders").get(0).get("name").asText()).isEqualTo("Content-Type");
        assertThat(data.get("requestParams").get(0).get("name").asText()).isEqualTo("username");
    }

    @Test
    @DisplayName("普通用户查看下线接口文档返回数据不存在")
    void shouldHideOfflineInterfaceDocForNormalUser() throws Exception {
        long id = createInterfaceInfo("offlineDocApi", "/api/offline_doc", InterfaceInfoStatusEnum.OFFLINE.getValue());

        mockMvc.perform(get("/interfaceDoc/get").param("interfaceInfoId", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    @DisplayName("管理员可以查看下线接口文档并看到 targetHost")
    void shouldAllowAdminViewOfflineInterfaceDoc() throws Exception {
        MockHttpSession adminSession = loginWithRole("ida" + suffix(), "admin");
        long id = createInterfaceInfo("adminDocApi", "/api/admin_doc", InterfaceInfoStatusEnum.OFFLINE.getValue());

        JsonNode data = requestDoc(id, adminSession)
                .get("data");

        assertThat(data.get("interfaceInfo").get("targetHost").asText()).isEqualTo(TEST_TARGET_HOST);
        assertThat(data.get("interfaceInfo").get("name").asText()).isEqualTo("adminDocApi");
    }

    @Test
    @DisplayName("存在结构化文档时优先返回结构化参数、示例和错误码")
    void shouldPreferStructuredDocWhenExists() throws Exception {
        long id = createInterfaceInfo("structuredDocApi", "/api/structured_doc", InterfaceInfoStatusEnum.ONLINE.getValue());
        saveStructuredDoc(id);

        JsonNode data = requestDoc(id, null)
                .get("data");

        assertThat(data.get("legacyFallback").asBoolean()).isFalse();
        assertThat(data.get("doc").get("successExample").asText()).contains("\"ok\":true");
        assertThat(data.get("requestParams").get(0).get("name").asText()).isEqualTo("first");
        assertThat(data.get("errorCodes").get(0).get("errorCode").asText()).isEqualTo("A001");
        assertThat(data.get("curlExample").asText()).contains("-d");
    }

    /**
     * 请求接口文档详情。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param session         登录会话
     * @return 响应 JSON
     */
    private JsonNode requestDoc(long interfaceInfoId, MockHttpSession session) throws Exception {
        String response = mockMvc.perform(get("/interfaceDoc/get")
                        .param("interfaceInfoId", String.valueOf(interfaceInfoId))
                        .session(session == null ? new MockHttpSession() : session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    /**
     * 创建测试接口。
     *
     * @param name   接口名称
     * @param path   接口路径
     * @param status 接口状态
     * @return 接口 ID
     */
    private long createInterfaceInfo(String name, String path, int status) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setName(name);
        interfaceInfo.setSdkMethodName("getUsernameByPost");
        interfaceInfo.setDescription("接口文档测试");
        interfaceInfo.setPath(path);
        interfaceInfo.setTargetHost(TEST_TARGET_HOST);
        interfaceInfo.setUrl(TEST_TARGET_HOST + path);
        interfaceInfo.setRequestParams("{\"username\":\"string\"}");
        interfaceInfo.setRequestHeader("Content-Type: application/json");
        interfaceInfo.setResponseHeader("{\"code\":0,\"data\":\"string\"}");
        interfaceInfo.setStatus(status);
        interfaceInfo.setMethod("POST");
        interfaceInfo.setUserId(1L);
        assertThat(interfaceInfoService.save(interfaceInfo)).isTrue();
        return interfaceInfo.getId();
    }

    /**
     * 保存结构化接口文档。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    private void saveStructuredDoc(long interfaceInfoId) {
        InterfaceDoc doc = new InterfaceDoc();
        doc.setInterfaceInfoId(interfaceInfoId);
        doc.setDocVersion("v1");
        doc.setRequestContentType("application/json");
        doc.setResponseContentType("application/json");
        doc.setAuthDescription("签名鉴权");
        doc.setSuccessExample("{\"ok\":true}");
        doc.setFailExample("{\"ok\":false}");
        assertThat(interfaceDocService.save(doc)).isTrue();

        InterfaceDocParam laterParam = buildParam(interfaceInfoId, "second", 2);
        InterfaceDocParam firstParam = buildParam(interfaceInfoId, "first", 1);
        assertThat(interfaceDocParamService.save(laterParam)).isTrue();
        assertThat(interfaceDocParamService.save(firstParam)).isTrue();

        InterfaceDocErrorCode laterError = buildErrorCode(interfaceInfoId, "B001", 2);
        InterfaceDocErrorCode firstError = buildErrorCode(interfaceInfoId, "A001", 1);
        assertThat(interfaceDocErrorCodeService.save(laterError)).isTrue();
        assertThat(interfaceDocErrorCodeService.save(firstError)).isTrue();
    }

    /**
     * 构建文档参数。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param name            参数名称
     * @param sortOrder       排序值
     * @return 文档参数
     */
    private InterfaceDocParam buildParam(long interfaceInfoId, String name, int sortOrder) {
        InterfaceDocParam param = new InterfaceDocParam();
        param.setInterfaceInfoId(interfaceInfoId);
        param.setParamScene(InterfaceDocParamSceneEnum.BODY.getValue());
        param.setName(name);
        param.setType("string");
        param.setRequired(1);
        param.setExampleValue(name + "Value");
        param.setDescription(name + "说明");
        param.setSortOrder(sortOrder);
        return param;
    }

    /**
     * 构建文档错误码。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param code            错误码
     * @param sortOrder       排序值
     * @return 文档错误码
     */
    private InterfaceDocErrorCode buildErrorCode(long interfaceInfoId, String code, int sortOrder) {
        InterfaceDocErrorCode errorCode = new InterfaceDocErrorCode();
        errorCode.setInterfaceInfoId(interfaceInfoId);
        errorCode.setErrorCode(code);
        errorCode.setErrorMessage("错误" + code);
        errorCode.setDescription("错误说明");
        errorCode.setSolution("检查请求参数");
        errorCode.setSortOrder(sortOrder);
        return errorCode;
    }

    /**
     * 使用指定角色登录。
     *
     * @param account 用户账号
     * @param role    用户角色
     * @return 登录会话
     */
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
     * 生成账号后缀。
     *
     * @return 账号后缀
     */
    private String suffix() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        return timestamp.substring(timestamp.length() - 4);
    }
}
