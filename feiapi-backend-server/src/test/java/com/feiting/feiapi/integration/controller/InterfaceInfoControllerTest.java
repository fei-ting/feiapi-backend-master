package com.feiting.feiapi.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.controller.InterfaceInfoController;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoAddRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoInvokeRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoQueryRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoUpdateRequest;
import com.feiting.feiapi.model.vo.InterfaceInfoVO;
import com.feiting.feiapi.model.dto.user.UserLoginRequest;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
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

    @Resource
    private MockMvc mockMvc;

    @Resource
    private UserService userService;

    @Resource
    private InterfaceInfoService interfaceInfoService;

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

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

    @Nested
    @DisplayName("POST /interfaceInfo/add 创建接口")
    class AddTests {

        @Test
        @DisplayName("管理员创建接口成功，数据库可查")
        void shouldAddInterfaceInfo() throws Exception {
            MockHttpSession session = loginAsAdmin();
            InterfaceInfoAddRequest request = buildAddRequest("addApi", "/api/add_test", "GET");

            MvcResult result = mockMvc.perform(post("/interfaceInfo/add")
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
        }

        @Test
        @DisplayName("普通用户创建接口返回无权限")
        void shouldDenyNormalUserAdd() throws Exception {
            MockHttpSession session = loginAsUser();
            InterfaceInfoAddRequest request = buildAddRequest("normalAddApi", "/api/normal_add", "GET");

            mockMvc.perform(post("/interfaceInfo/add")
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
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(50001));
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
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deleteJson)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));

            assertNotNull(interfaceInfoService.getById(id));
        }

        @Test
        @DisplayName("管理员删除接口成功")
        void shouldAllowAdminToDelete() throws Exception {
            String timestamp = String.valueOf(System.currentTimeMillis());
            MockHttpSession adminSession = loginWithRole("adl" + timestamp.substring(timestamp.length() - 4), "admin");
            long id = createInterfaceInfo("adminDelApi", "/api/admin_del", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());
            String deleteJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/interfaceInfo/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deleteJson)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            assertNull(interfaceInfoService.getById(id));
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            MockHttpSession session = loginAsAdmin();
            String deleteJson = "{\"id\":0}";

            mockMvc.perform(post("/interfaceInfo/delete")
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
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":" + id + "}")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(50001));
        }
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
            MockHttpSession adminSession = loginAsAdmin();
            long id = createInterfaceInfo("onlineApi", "/api/online_test", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());

            // 发布（会因网关不可用而失败，但应验证状态机转换）
            String onlineJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/interfaceInfo/online")
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
        @DisplayName("非管理员发布应返回无权限")
        void shouldDenyNonAdmin() throws Exception {
            String timestamp = String.valueOf(System.currentTimeMillis());
            MockHttpSession userSession = loginWithRole("uon" + timestamp.substring(timestamp.length() - 4), "user");
            long id = createInterfaceInfo("onlineApi2", "/api/online_test2", "GET", InterfaceInfoStatusEnum.OFFLINE.getValue());

            String onlineJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/interfaceInfo/online")
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

        @Test
        @DisplayName("接口不存在返回数据不存在")
        void shouldFailWhenInterfaceNotFound() throws Exception {
            MockHttpSession session = loginAsUser();

            InterfaceInfoInvokeRequest request = new InterfaceInfoInvokeRequest();
            request.setId(99999L);
            request.setUserRequestParams("{\"name\":\"test\"}");

            mockMvc.perform(post("/interfaceInfo/invoke")
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
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("请求参数缺少必填字段：username"));
        }
    }
}
