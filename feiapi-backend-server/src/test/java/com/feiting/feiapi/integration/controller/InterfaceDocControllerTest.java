package com.feiting.feiapi.integration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiting.feiapi.constant.UserConstant;
import com.feiting.feiapi.model.dto.interfaceDoc.InterfaceDocErrorCodeSaveRequest;
import com.feiting.feiapi.model.dto.interfaceDoc.InterfaceDocParamSaveRequest;
import com.feiting.feiapi.model.dto.interfaceDoc.InterfaceDocSaveRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoAddRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoUpdateRequest;
import com.feiting.feiapi.model.dto.user.UserLoginRequest;
import com.feiting.feiapi.model.entity.InterfaceDoc;
import com.feiting.feiapi.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.model.entity.InterfaceDocParam;
import com.feiting.feiapi.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import com.feiting.feiapi.service.InterfaceDocParamService;
import com.feiting.feiapi.service.InterfaceDocService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口文档查询与维护控制器集成测试。
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
     * 测试账号序列，确保账号符合 4-10 位规则且不重复。
     */
    private static final AtomicInteger ACCOUNT_SEQUENCE = new AtomicInteger(1000);

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

    /**
     * 用户接口关系服务。
     */
    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    /**
     * 测试缺少结构化文档时的公开空状态与字段脱敏。
     */
    @Test
    @DisplayName("上线接口没有结构化文档时返回空状态，普通用户不返回旧字段和内部地址")
    void shouldReturnEmptyDocStateWhenStructuredDocMissing() throws Exception {
        long id = createInterfaceInfo("legacyDocApi", "/api/legacy_doc_" + suffix(),
                InterfaceInfoStatusEnum.ONLINE.getValue(), "POST", "{\"username\":\"string\"}");

        JsonNode data = requestDoc(id, null).get("data");

        assertThat(data.has("legacyFallback")).isFalse();
        assertThat(data.get("structuredDocMissing").asBoolean()).isTrue();
        assertThat(data.get("gatewayUrl").asText()).isEqualTo("http://localhost:8090/api/legacy_doc_" + suffixValueFromPath(data.get("gatewayUrl").asText()));
        assertThat(data.get("interfaceInfo").get("url").isNull()).isTrue();
        assertThat(data.get("interfaceInfo").get("targetHost").isNull()).isTrue();
        assertThat(data.get("interfaceInfo").has("requestParams")).isFalse();
        assertThat(data.get("interfaceInfo").has("requestHeader")).isFalse();
        assertThat(data.get("interfaceInfo").has("responseHeader")).isFalse();
        assertThat(data.get("interfaceInfo").has("sdkMethodName")).isFalse();
        assertThat(data.get("interfaceInfo").has("userId")).isFalse();
        assertThat(data.get("requestHeaders")).isEmpty();
        assertThat(data.get("requestParams")).isEmpty();
        assertThat(data.get("responseParams")).isEmpty();
    }

    /**
     * 测试普通用户无法查看下线接口文档。
     */
    @Test
    @DisplayName("普通用户查看下线接口文档返回数据不存在")
    void shouldHideOfflineInterfaceDocForNormalUser() throws Exception {
        long id = createInterfaceInfo("offlineDocApi", "/api/offline_doc_" + suffix(),
                InterfaceInfoStatusEnum.OFFLINE.getValue(), "POST", "{\"username\":\"string\"}");

        mockMvc.perform(get("/interfaceDoc/get").param("interfaceInfoId", String.valueOf(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /**
     * 测试管理员可以查看下线接口文档和内部地址。
     */
    @Test
    @DisplayName("管理员可以查看下线接口文档并看到内部地址")
    void shouldAllowAdminViewOfflineInterfaceDoc() throws Exception {
        MockHttpSession adminSession = loginWithRole("idadmin" + suffix(), "admin");
        long id = createInterfaceInfo("adminDocApi", "/api/admin_doc_" + suffix(),
                InterfaceInfoStatusEnum.OFFLINE.getValue(), "POST", "{\"username\":\"string\"}");

        JsonNode data = requestDoc(id, adminSession).get("data");

        assertThat(data.get("interfaceInfo").get("targetHost").asText()).isEqualTo(TEST_TARGET_HOST);
        assertThat(data.get("interfaceInfo").get("url").asText()).contains(TEST_TARGET_HOST);
        assertThat(data.get("interfaceInfo").get("name").asText()).isEqualTo("adminDocApi");
    }

    /**
     * 测试聚合保存后返回结构化文档与签名脚本。
     */
    @Test
    @DisplayName("管理员聚合保存接口文档后返回结构化参数、nullable 和签名脚本")
    void shouldSaveDocAndReturnStructuredDoc() throws Exception {
        MockHttpSession adminSession = loginWithRole("idsave" + suffix(), "admin");
        String template = "{\"name\":\"string\",\"age\":18,\"enabled\":true,\"meta\":{},\"tags\":[]}";
        long id = createInterfaceInfo("saveDocApi", "/api/save_doc_" + suffix(),
                InterfaceInfoStatusEnum.ONLINE.getValue(), "POST", template);

        saveDocByAdmin(adminSession, buildFullSaveRequest(id));

        JsonNode data = requestDoc(id, adminSession).get("data");
        JsonNode requestParams = data.get("requestParams");
        JsonNode responseParams = data.get("responseParams");
        String curlExample = data.get("curlExample").asText();

        assertThat(data.get("structuredDocMissing").asBoolean()).isFalse();
        assertThat(data.get("doc").get("successExample").asText()).contains("\"ok\":true");
        assertThat(requestParams).hasSize(5);
        assertThat(requestParams.findValuesAsText("type")).contains("string", "number", "boolean", "object", "array");
        assertThat(responseParams).hasSize(2);
        assertThat(responseParams.get(0).get("required").asBoolean()).isTrue();
        assertThat(responseParams.get(0).get("nullable").asBoolean()).isFalse();
        assertThat(responseParams.get(1).get("required").asBoolean()).isFalse();
        assertThat(responseParams.get(1).get("nullable").asBoolean()).isTrue();
        assertThat(curlExample)
                .contains("ACCESS_KEY", "SECRET_KEY", "openssl dgst -sha256 -hmac",
                        "accessKey", "nonce", "timestamp", "sign", "CANONICAL_STRING");
        assertThat(curlExample)
                .contains("\"age\":18", "\"enabled\":true", "\"meta\":{}", "\"tags\":[]")
                .doesNotContain("\"age\":\"18\"", "\"enabled\":\"true\"");
    }

    /**
     * 测试非管理员无法保存接口文档。
     */
    @Test
    @DisplayName("非管理员不能调用聚合保存接口")
    void shouldRejectNonAdminSaveDoc() throws Exception {
        MockHttpSession userSession = loginWithRole("iduser" + suffix(), "user");
        long id = createInterfaceInfo("normalSaveApi", "/api/normal_save_" + suffix(),
                InterfaceInfoStatusEnum.ONLINE.getValue(), "POST", "{\"username\":\"string\"}");

        JsonNode response = postSave(userSession, buildBasicSaveRequest(id));

        assertThat(response.get("code").asInt()).isEqualTo(40101);
    }

    /**
     * 测试聚合保存接口拒绝非法内容。
     */
    @Test
    @DisplayName("聚合保存接口拒绝非法 JSON、敏感信息、脚本、重复错误码和数量超限")
    void shouldRejectInvalidSaveDocRequests() throws Exception {
        MockHttpSession adminSession = loginWithRole("idinvalid" + suffix(), "admin");
        long id = createInterfaceInfo("invalidSaveApi", "/api/invalid_save_" + suffix(),
                InterfaceInfoStatusEnum.ONLINE.getValue(), "POST", "{\"username\":\"string\"}");

        InterfaceDocSaveRequest illegalJsonRequest = buildBasicSaveRequest(id);
        illegalJsonRequest.setSuccessExample("{bad json");
        assertSaveFailed(adminSession, illegalJsonRequest, 40000);

        InterfaceDocSaveRequest sensitiveRequest = buildBasicSaveRequest(id);
        sensitiveRequest.setSuccessExample("{\"phone\":\"13800138000\"}");
        assertSaveFailed(adminSession, sensitiveRequest, 40000);

        InterfaceDocSaveRequest accessKeyRequest = buildBasicSaveRequest(id);
        accessKeyRequest.setSuccessExample("{\"accessKey\":\"abcd1234\"}");
        assertSaveFailed(adminSession, accessKeyRequest, 40000);

        InterfaceDocSaveRequest scriptRequest = buildBasicSaveRequest(id);
        scriptRequest.getParams().add(param("resp", null, "RESPONSE", "data", "string", true, false, 2));
        scriptRequest.getParams().get(1).setValidationRule("<script>alert(1)</script>");
        assertSaveFailed(adminSession, scriptRequest, 40000);

        InterfaceDocSaveRequest duplicateErrorRequest = buildBasicSaveRequest(id);
        duplicateErrorRequest.getErrorCodes().add(errorCode("A001", "错误 A", 1));
        duplicateErrorRequest.getErrorCodes().add(errorCode("A001", "错误 A2", 2));
        assertSaveFailed(adminSession, duplicateErrorRequest, 40000);

        InterfaceDocSaveRequest paramLimitRequest = buildBasicSaveRequest(id);
        paramLimitRequest.setParams(IntStream.rangeClosed(1, 201)
                .mapToObj(index -> param("p" + index, null, "RESPONSE", "field" + index, "string", false, true, index))
                .collect(java.util.stream.Collectors.toList()));
        assertSaveFailed(adminSession, paramLimitRequest, 40000);

        InterfaceDocSaveRequest errorLimitRequest = buildBasicSaveRequest(id);
        errorLimitRequest.setErrorCodes(IntStream.rangeClosed(1, 101)
                .mapToObj(index -> errorCode("E" + index, "错误" + index, index))
                .collect(java.util.stream.Collectors.toList()));
        assertSaveFailed(adminSession, errorLimitRequest, 40000);
    }

    /**
     * 测试参数层级关系校验。
     */
    @Test
    @DisplayName("聚合保存接口拒绝非法父级、跨场景引用、循环引用和过深层级")
    void shouldRejectInvalidParamHierarchy() throws Exception {
        MockHttpSession adminSession = loginWithRole("idhierarchy" + suffix(), "admin");
        long id = createInterfaceInfo("hierarchyApi", "/api/hierarchy_" + suffix(),
                InterfaceInfoStatusEnum.ONLINE.getValue(), "POST", "{\"username\":\"string\"}");

        InterfaceDocSaveRequest missingParentRequest = buildBasicSaveRequest(id);
        missingParentRequest.getParams().add(param("resp", "missing", "RESPONSE", "data", "string", true, false, 2));
        assertSaveFailed(adminSession, missingParentRequest, 40000);

        InterfaceDocSaveRequest crossSceneRequest = buildBasicSaveRequest(id);
        crossSceneRequest.getParams().add(param("bodyChild", "bodyUsername", "BODY", "child", "string", true, false, 2));
        assertSaveFailed(adminSession, crossSceneRequest, 40000);

        InterfaceDocSaveRequest cycleRequest = buildBasicSaveRequest(id);
        cycleRequest.getParams().add(param("respA", "respB", "RESPONSE", "a", "object", true, false, 2));
        cycleRequest.getParams().add(param("respB", "respA", "RESPONSE", "b", "object", true, false, 3));
        assertSaveFailed(adminSession, cycleRequest, 40000);

        InterfaceDocSaveRequest depthRequest = buildBasicSaveRequest(id);
        String parentKey = null;
        for (int index = 1; index <= 9; index++) {
            String key = "respDepth" + index;
            depthRequest.getParams().add(param(key, parentKey, "RESPONSE", "depth" + index, "object", true, false, index + 1));
            parentKey = key;
        }
        assertSaveFailed(adminSession, depthRequest, 40000);
    }

    /**
     * 测试子项数据库保存失败时聚合保存整体回滚。
     */
    @Test
    @DisplayName("聚合保存任一子项数据库失败时整体回滚")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldRollbackWhenChildSaveFails() throws Exception {
        MockHttpSession adminSession = null;
        Long interfaceInfoId = null;
        try {
            adminSession = loginWithRole("idrollback" + suffix(), "admin");
            interfaceInfoId = createInterfaceInfo("rollbackApi", "/api/rollback_" + suffix(),
                    InterfaceInfoStatusEnum.ONLINE.getValue(), "POST", "{\"username\":\"string\"}");
            InterfaceDocSaveRequest validRequest = buildBasicSaveRequest(interfaceInfoId);
            validRequest.getErrorCodes().add(errorCode("A001", "错误 A", 1));
            saveDocByAdmin(adminSession, validRequest);

            InterfaceDocSaveRequest invalidRequest = buildBasicSaveRequest(interfaceInfoId);
            invalidRequest.setDocVersion("v2");
            InterfaceDocErrorCodeSaveRequest tooLongError = errorCode("B001", "错误 B", 1);
            tooLongError.setErrorMessage(repeatText("错误", 200));
            invalidRequest.getErrorCodes().add(tooLongError);

            Long finalInterfaceInfoId = interfaceInfoId;
            assertThatThrownBy(() -> interfaceDocService.saveDoc(invalidRequest))
                    .isInstanceOf(RuntimeException.class);

            InterfaceDoc doc = interfaceDocService.lambdaQuery().eq(InterfaceDoc::getInterfaceInfoId, finalInterfaceInfoId).one();
            List<InterfaceDocErrorCode> errorCodes = interfaceDocErrorCodeService.lambdaQuery()
                    .eq(InterfaceDocErrorCode::getInterfaceInfoId, finalInterfaceInfoId)
                    .list();
            assertThat(doc.getDocVersion()).isEqualTo("v1");
            assertThat(errorCodes).hasSize(1);
            assertThat(errorCodes.get(0).getErrorCode()).isEqualTo("A001");
        } finally {
            cleanupCommittedRollbackTestData(interfaceInfoId, adminSession);
        }
    }

    /**
     * 测试接口普通更新不会覆盖人工文档，模板变化时才对账同步。
     */
    @Test
    @DisplayName("普通接口信息更新不覆盖人工文档，模板变化时按对账同步")
    void shouldSyncRequestDocOnlyWhenTemplateChangesAndPreserveManualFields() throws Exception {
        MockHttpSession adminSession = loginWithRole("idsync" + suffix(), "admin");
        long id = createInterfaceInfo("syncDocApi", "/api/sync_doc_" + suffix(),
                InterfaceInfoStatusEnum.ONLINE.getValue(), "POST", "{\"username\":\"string\",\"age\":18}");
        InterfaceInfo interfaceInfo = interfaceInfoService.getById(id);
        interfaceDocService.syncRequestDocFromInterfaceInfo(interfaceInfo);
        InterfaceDocParam usernameParam = interfaceDocParamService.lambdaQuery()
                .eq(InterfaceDocParam::getInterfaceInfoId, id)
                .eq(InterfaceDocParam::getName, "username")
                .one();
        usernameParam.setDescription("人工维护说明");
        usernameParam.setExampleValue("alice");
        usernameParam.setSortOrder(9);
        assertThat(interfaceDocParamService.updateById(usernameParam)).isTrue();

        InterfaceInfoUpdateRequest normalUpdateRequest = new InterfaceInfoUpdateRequest();
        normalUpdateRequest.setId(id);
        normalUpdateRequest.setDescription("只修改接口描述");
        updateInterfaceInfo(adminSession, normalUpdateRequest);
        InterfaceDocParam afterNormalUpdate = interfaceDocParamService.getById(usernameParam.getId());
        assertThat(afterNormalUpdate.getDescription()).isEqualTo("人工维护说明");
        assertThat(afterNormalUpdate.getExampleValue()).isEqualTo("alice");
        assertThat(afterNormalUpdate.getSortOrder()).isEqualTo(9);

        InterfaceInfoUpdateRequest templateUpdateRequest = new InterfaceInfoUpdateRequest();
        templateUpdateRequest.setId(id);
        templateUpdateRequest.setRequestParams("{\"username\":\"string\",\"enabled\":true}");
        updateInterfaceInfo(adminSession, templateUpdateRequest);
        List<InterfaceDocParam> params = interfaceDocParamService.lambdaQuery()
                .eq(InterfaceDocParam::getInterfaceInfoId, id)
                .orderByAsc(InterfaceDocParam::getName)
                .list();

        assertThat(params).extracting(InterfaceDocParam::getName).containsExactly("enabled", "username");
        InterfaceDocParam preservedUsernameParam = params.stream()
                .filter(param -> "username".equals(param.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("username 参数应该存在"));
        InterfaceDocParam enabledParam = params.stream()
                .filter(param -> "enabled".equals(param.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("enabled 参数应该存在"));
        assertThat(preservedUsernameParam.getDescription()).isEqualTo("人工维护说明");
        assertThat(preservedUsernameParam.getExampleValue()).isEqualTo("alice");
        assertThat(preservedUsernameParam.getSortOrder()).isEqualTo(9);
        assertThat(enabledParam.getType()).isEqualTo("boolean");
        assertThat(enabledParam.getNullable()).isZero();
    }

    /**
     * 测试新增接口时自动生成结构化请求参数。
     */
    @Test
    @DisplayName("管理员新增接口时根据运行时参数模板生成结构化请求参数")
    void shouldCreateStructuredRequestParamsWhenAdminAddsInterface() throws Exception {
        MockHttpSession adminSession = loginWithRole("idadddoc" + suffix(), "admin");
        InterfaceInfoAddRequest addRequest = new InterfaceInfoAddRequest();
        addRequest.setName("autoDocApi");
        addRequest.setSdkMethodName("getUsernameByPost");
        addRequest.setDescription("自动生成结构化文档");
        addRequest.setPath("/api/auto_doc_" + suffix());
        addRequest.setTargetHost(TEST_TARGET_HOST);
        addRequest.setRequestParams("{\"username\":\"string\"}");
        addRequest.setMethod("POST");

        String response = mockMvc.perform(post("/interfaceInfo/add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addRequest))
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(response).get("data").asLong();

        JsonNode data = requestDoc(id, adminSession).get("data");

        assertThat(data.get("structuredDocMissing").asBoolean()).isFalse();
        assertThat(data.get("requestParams")).hasSize(1);
        assertThat(data.get("requestParams").get(0).get("name").asText()).isEqualTo("username");
        assertThat(data.get("requestParams").get(0).get("paramScene").asText()).isEqualTo("BODY");
        assertThat(data.get("requestParams").get(0).get("type").asText()).isEqualTo("string");
        assertThat(data.get("requestParams").get(0).get("required").asBoolean()).isTrue();
        assertThat(data.get("requestParams").get(0).get("nullable").asBoolean()).isFalse();
    }

    /**
     * 测试逻辑删除后可以重复重建文档和错误码。
     */
    @Test
    @DisplayName("文档和错误码支持重复删除、重建、再次删除")
    void shouldAllowLogicalDeleteAndRecreateRepeatedly() {
        long id = createInterfaceInfo("deleteRecreateApi", "/api/delete_recreate_" + suffix(),
                InterfaceInfoStatusEnum.ONLINE.getValue(), "POST", "{\"username\":\"string\"}");
        InterfaceDoc firstDoc = buildDoc(id, "v1");
        assertThat(interfaceDocService.save(firstDoc)).isTrue();
        assertThat(interfaceDocService.removeById(firstDoc.getId())).isTrue();
        InterfaceDoc secondDoc = buildDoc(id, "v2");
        assertThat(interfaceDocService.save(secondDoc)).isTrue();
        assertThat(interfaceDocService.removeById(secondDoc.getId())).isTrue();

        InterfaceDocErrorCode firstErrorCode = buildErrorCode(id, "A001", 1);
        assertThat(interfaceDocErrorCodeService.save(firstErrorCode)).isTrue();
        assertThat(interfaceDocErrorCodeService.removeById(firstErrorCode.getId())).isTrue();
        InterfaceDocErrorCode secondErrorCode = buildErrorCode(id, "A001", 1);
        assertThat(interfaceDocErrorCodeService.save(secondErrorCode)).isTrue();
        assertThat(interfaceDocErrorCodeService.removeById(secondErrorCode.getId())).isTrue();
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
     * 管理员保存接口文档。
     *
     * @param adminSession 管理员会话
     * @param request      保存请求
     */
    private void saveDocByAdmin(MockHttpSession adminSession, InterfaceDocSaveRequest request) throws Exception {
        mockMvc.perform(post("/interfaceDoc/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }

    /**
     * 发起保存接口文档请求。
     *
     * @param session 会话
     * @param request 保存请求
     * @return 响应 JSON
     */
    private JsonNode postSave(MockHttpSession session, InterfaceDocSaveRequest request) throws Exception {
        String response = mockMvc.perform(post("/interfaceDoc/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    /**
     * 断言保存请求失败。
     *
     * @param adminSession 管理员会话
     * @param request      保存请求
     * @param code         预期错误码
     */
    private void assertSaveFailed(MockHttpSession adminSession, InterfaceDocSaveRequest request, int code) throws Exception {
        JsonNode response = postSave(adminSession, request);
        assertThat(response.get("code").asInt()).isEqualTo(code);
    }

    /**
     * 更新接口信息。
     *
     * @param adminSession 管理员会话
     * @param request      更新请求
     */
    private void updateInterfaceInfo(MockHttpSession adminSession, InterfaceInfoUpdateRequest request) throws Exception {
        mockMvc.perform(post("/interfaceInfo/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }

    /**
     * 创建测试接口。
     *
     * @param name          接口名称
     * @param path          接口路径
     * @param status        接口状态
     * @param method        请求方法
     * @param requestParams 请求参数模板
     * @return 接口 ID
     */
    private long createInterfaceInfo(String name, String path, int status, String method, String requestParams) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setName(name);
        interfaceInfo.setSdkMethodName("getUsernameByPost");
        interfaceInfo.setDescription("接口文档测试");
        interfaceInfo.setPath(path);
        interfaceInfo.setTargetHost(TEST_TARGET_HOST);
        interfaceInfo.setUrl(TEST_TARGET_HOST + path);
        interfaceInfo.setRequestParams(requestParams);
        interfaceInfo.setRequestHeader("Content-Type: application/json");
        interfaceInfo.setResponseHeader("{\"code\":0,\"data\":\"string\"}");
        interfaceInfo.setStatus(status);
        interfaceInfo.setMethod(method);
        interfaceInfo.setUserId(1L);
        assertThat(interfaceInfoService.save(interfaceInfo)).isTrue();
        return interfaceInfo.getId();
    }

    /**
     * 构建完整保存请求。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 保存请求
     */
    private InterfaceDocSaveRequest buildFullSaveRequest(long interfaceInfoId) {
        InterfaceDocSaveRequest request = baseSaveRequest(interfaceInfoId);
        List<InterfaceDocParamSaveRequest> params = new ArrayList<>();
        params.add(param("bodyName", null, "BODY", "name", "string", true, false, 1));
        params.add(param("bodyAge", null, "BODY", "age", "number", true, false, 2));
        params.add(param("bodyEnabled", null, "BODY", "enabled", "boolean", true, false, 3));
        params.add(param("bodyMeta", null, "BODY", "meta", "object", true, false, 4));
        params.add(param("bodyTags", null, "BODY", "tags", "array", true, false, 5));
        params.add(param("respData", null, "RESPONSE", "data", "object", true, false, 6));
        params.add(param("respNickname", "respData", "RESPONSE", "nickname", "string", false, true, 7));
        request.setParams(params);
        request.getErrorCodes().add(errorCode("A001", "参数错误", 1));
        return request;
    }

    /**
     * 构建基础保存请求。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 保存请求
     */
    private InterfaceDocSaveRequest buildBasicSaveRequest(long interfaceInfoId) {
        InterfaceDocSaveRequest request = baseSaveRequest(interfaceInfoId);
        request.getParams().add(param("bodyUsername", null, "BODY", "username", "string", true, false, 1));
        return request;
    }

    /**
     * 构建保存请求基础字段。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 保存请求
     */
    private InterfaceDocSaveRequest baseSaveRequest(long interfaceInfoId) {
        InterfaceDocSaveRequest request = new InterfaceDocSaveRequest();
        request.setInterfaceInfoId(interfaceInfoId);
        request.setDocVersion("v1");
        request.setRequestContentType("application/json");
        request.setResponseContentType("application/json");
        request.setAuthDescription("通过平台签名鉴权");
        request.setSuccessExample("{\"ok\":true}");
        request.setFailExample("{\"ok\":false}");
        request.setRemark("公开文档");
        request.setParams(new ArrayList<>());
        request.setErrorCodes(new ArrayList<>());
        return request;
    }

    /**
     * 构建参数保存请求。
     *
     * @param key       参数键
     * @param parentKey 父级参数键
     * @param scene     参数场景
     * @param name      参数名称
     * @param type      参数类型
     * @param required  是否必填
     * @param nullable  是否允许为空
     * @param sortOrder 排序值
     * @return 参数保存请求
     */
    private InterfaceDocParamSaveRequest param(String key,
                                               String parentKey,
                                               String scene,
                                               String name,
                                               String type,
                                               boolean required,
                                               Boolean nullable,
                                               int sortOrder) {
        InterfaceDocParamSaveRequest request = new InterfaceDocParamSaveRequest();
        request.setParamKey(key);
        request.setParentParamKey(parentKey);
        request.setParamScene(scene);
        request.setName(name);
        request.setType(type);
        request.setRequired(required);
        request.setNullable(nullable);
        request.setDescription(name + "说明");
        request.setDefaultValue("");
        request.setExampleValue(exampleValue(type, name));
        request.setValidationRule("");
        request.setSortOrder(sortOrder);
        return request;
    }

    /**
     * 构建示例值。
     *
     * @param type 参数类型
     * @param name 参数名称
     * @return 示例值
     */
    private String exampleValue(String type, String name) {
        if ("number".equals(type)) {
            return "18";
        }
        if ("boolean".equals(type)) {
            return "true";
        }
        if ("object".equals(type)) {
            return "{}";
        }
        if ("array".equals(type)) {
            return "[]";
        }
        return name + "Value";
    }

    /**
     * 构建错误码保存请求。
     *
     * @param code      错误码
     * @param message   错误信息
     * @param sortOrder 排序值
     * @return 错误码保存请求
     */
    private InterfaceDocErrorCodeSaveRequest errorCode(String code, String message, int sortOrder) {
        InterfaceDocErrorCodeSaveRequest request = new InterfaceDocErrorCodeSaveRequest();
        request.setErrorCode(code);
        request.setErrorMessage(message);
        request.setDescription("错误说明");
        request.setSolution("检查请求参数");
        request.setSortOrder(sortOrder);
        return request;
    }

    /**
     * 构建文档主信息。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param version         文档版本
     * @return 文档主信息
     */
    private InterfaceDoc buildDoc(long interfaceInfoId, String version) {
        InterfaceDoc doc = new InterfaceDoc();
        doc.setInterfaceInfoId(interfaceInfoId);
        doc.setDocVersion(version);
        doc.setRequestContentType("application/json");
        doc.setResponseContentType("application/json");
        doc.setAuthDescription("签名鉴权");
        return doc;
    }

    /**
     * 构建错误码实体。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param code            错误码
     * @param sortOrder       排序值
     * @return 错误码实体
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
     * 清理非事务回滚用例真实提交的测试数据。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param adminSession    管理员登录会话
     */
    private void cleanupCommittedRollbackTestData(Long interfaceInfoId, MockHttpSession adminSession) {
        if (interfaceInfoId != null && interfaceInfoId > 0) {
            interfaceDocErrorCodeService.lambdaUpdate()
                    .eq(InterfaceDocErrorCode::getInterfaceInfoId, interfaceInfoId)
                    .remove();
            interfaceDocParamService.lambdaUpdate()
                    .eq(InterfaceDocParam::getInterfaceInfoId, interfaceInfoId)
                    .remove();
            interfaceDocService.lambdaUpdate()
                    .eq(InterfaceDoc::getInterfaceInfoId, interfaceInfoId)
                    .remove();
            userInterfaceInfoService.lambdaUpdate()
                    .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                    .remove();
            interfaceInfoService.removeById(interfaceInfoId);
        }
        User adminUser = adminSession == null ? null : (User) adminSession.getAttribute(UserConstant.USER_LOGIN_STATE);
        if (adminUser != null && adminUser.getId() != null) {
            userInterfaceInfoService.lambdaUpdate()
                    .eq(UserInterfaceInfo::getUserId, adminUser.getId())
                    .remove();
            userService.removeById(adminUser.getId());
        }
    }

    /**
     * 使用指定角色登录。
     *
     * @param account 用户账号
     * @param role    用户角色
     * @return 登录会话
     */
    private MockHttpSession loginWithRole(String account, String role) throws Exception {
        String userAccount = buildValidAccount(account);
        userService.userRegister(userAccount, "password123", "password123");
        User user = userService.lambdaQuery().eq(User::getUserAccount, userAccount).one();
        user.setUserRole(role);
        userService.updateById(user);

        UserLoginRequest loginRequest = new UserLoginRequest();
        loginRequest.setUserAccount(userAccount);
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
     * 构建符合项目规则的测试账号。
     *
     * @param seed 原始账号种子
     * @return 合法测试账号
     */
    private String buildValidAccount(String seed) {
        String normalizedSeed = seed == null ? "user" : seed.replaceAll("[^A-Za-z0-9]", "");
        if (normalizedSeed.isEmpty() || !Character.isLetter(normalizedSeed.charAt(0))) {
            normalizedSeed = "u" + normalizedSeed;
        }
        String prefix = normalizedSeed.substring(0, Math.min(5, normalizedSeed.length()));
        return prefix + ACCOUNT_SEQUENCE.getAndIncrement();
    }

    /**
     * 重复拼接文本。
     *
     * @param text  原始文本
     * @param count 重复次数
     * @return 重复后的文本
     */
    private String repeatText(String text, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> text)
                .collect(Collectors.joining());
    }

    /**
     * 生成唯一后缀。
     *
     * @return 唯一后缀
     */
    private String suffix() {
        return String.valueOf(System.nanoTime());
    }

    /**
     * 从路径中读取后缀。
     *
     * @param path 路径
     * @return 后缀
     */
    private String suffixValueFromPath(String path) {
        int index = path.lastIndexOf("_");
        return index < 0 ? "" : path.substring(index + 1);
    }
}
