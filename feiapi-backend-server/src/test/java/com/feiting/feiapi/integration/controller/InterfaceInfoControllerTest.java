package com.feiting.feiapi.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoAddRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoInvokeRequest;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoUpdateRequest;
import com.feiting.feiapi.model.dto.user.UserLoginRequest;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

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

    @Resource
    private MockMvc mockMvc;

    @Resource
    private UserService userService;

    @Resource
    private InterfaceInfoService interfaceInfoService;

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

    private MockHttpSession loginAsAdmin() throws Exception {
        return loginWithRole("admin_if_" + System.currentTimeMillis(), "admin");
    }

    private MockHttpSession loginAsUser() throws Exception {
        return loginWithRole("user_if_" + System.currentTimeMillis(), "user");
    }

    private InterfaceInfoAddRequest buildAddRequest(String name, String url, String method) {
        InterfaceInfoAddRequest request = new InterfaceInfoAddRequest();
        request.setName(name);
        request.setDescription("desc_" + name);
        request.setUrl(url);
        request.setRequestHeader("{\"Content-Type\":\"application/json\"}");
        request.setResponseHeader("{\"Content-Type\":\"application/json\"}");
        request.setMethod(method);
        return request;
    }

    @Nested
    @DisplayName("POST /interfaceInfo/add 创建接口")
    class AddTests {

        @Test
        @DisplayName("已登录用户创建接口成功，数据库可查")
        void shouldAddInterfaceInfo() throws Exception {
            MockHttpSession session = loginAsUser();
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
            assertEquals("/api/add_test", saved.getUrl());
            assertEquals(InterfaceInfoStatusEnum.OFFLINE.getValue(), saved.getStatus());
        }

        @Test
        @DisplayName("请求体为空返回参数错误")
        void shouldFailWhenBodyNull() throws Exception {
            MockHttpSession session = loginAsUser();

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
            MockHttpSession session = loginAsUser();
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
            MockHttpSession session = loginAsUser();
            InterfaceInfoAddRequest addRequest = buildAddRequest("getApi", "/api/get_test", "GET");

            String response = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(session))
                    .andReturn().getResponse().getContentAsString();

            long id = objectMapper.readTree(response).get("data").asLong();

            mockMvc.perform(get("/interfaceInfo/get").param("id", String.valueOf(id)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.name").value("getApi"))
                    .andExpect(jsonPath("$.data.url").value("/api/get_test"));
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            mockMvc.perform(get("/interfaceInfo/get").param("id", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("不存在的 id 返回 null data")
        void shouldReturnNullForNonExistent() throws Exception {
            mockMvc.perform(get("/interfaceInfo/get").param("id", "99999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Nested
    @DisplayName("POST /interfaceInfo/update 更新接口")
    class UpdateTests {

        @Test
        @DisplayName("本人更新成功，数据库状态已变化")
        void shouldUpdateOwnInterface() throws Exception {
            MockHttpSession session = loginAsUser();
            InterfaceInfoAddRequest addRequest = buildAddRequest("updateApi", "/api/update_test", "GET");

            String createResponse = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(session))
                    .andReturn().getResponse().getContentAsString();

            long id = objectMapper.readTree(createResponse).get("data").asLong();

            InterfaceInfoUpdateRequest updateRequest = new InterfaceInfoUpdateRequest();
            updateRequest.setId(id);
            updateRequest.setName("updatedApi");
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
            assertEquals("/api/updated", updated.getUrl());
            assertEquals("POST", updated.getMethod());
        }

        @Test
        @DisplayName("非本人且非管理员更新应失败")
        void shouldFailWhenNotOwner() throws Exception {
            MockHttpSession ownerSession = loginWithRole("owner_upd_" + System.currentTimeMillis(), "user");
            InterfaceInfoAddRequest addRequest = buildAddRequest("ownApi", "/api/own_upd", "GET");

            String createResponse = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(ownerSession))
                    .andReturn().getResponse().getContentAsString();

            long id = objectMapper.readTree(createResponse).get("data").asLong();

            // 其他用户尝试更新
            MockHttpSession otherSession = loginWithRole("other_upd_" + System.currentTimeMillis(), "user");
            InterfaceInfoUpdateRequest updateRequest = new InterfaceInfoUpdateRequest();
            updateRequest.setId(id);
            updateRequest.setName("hacked");

            mockMvc.perform(post("/interfaceInfo/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest))
                            .session(otherSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));

            // 验证未被修改
            InterfaceInfo unchanged = interfaceInfoService.getById(id);
            assertEquals("ownApi", unchanged.getName());
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            MockHttpSession session = loginAsUser();

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
            MockHttpSession session = loginAsUser();

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
    }

    @Nested
    @DisplayName("POST /interfaceInfo/delete 删除接口")
    class DeleteTests {

        @Test
        @DisplayName("本人删除成功，数据库逻辑删除")
        void shouldDeleteOwnInterface() throws Exception {
            MockHttpSession session = loginAsUser();
            InterfaceInfoAddRequest addRequest = buildAddRequest("deleteApi", "/api/delete_test", "GET");

            String createResponse = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(session))
                    .andReturn().getResponse().getContentAsString();

            long id = objectMapper.readTree(createResponse).get("data").asLong();

            String deleteJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/interfaceInfo/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deleteJson)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            // 验证已被逻辑删除
            InterfaceInfo deleted = interfaceInfoService.getById(id);
            assertNull(deleted, "逻辑删除后应查不到");
        }

        @Test
        @DisplayName("非本人且非管理员删除应失败")
        void shouldFailWhenNotOwner() throws Exception {
            MockHttpSession ownerSession = loginWithRole("owner_del_" + System.currentTimeMillis(), "user");
            InterfaceInfoAddRequest addRequest = buildAddRequest("ownDelApi", "/api/own_del", "GET");

            String createResponse = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(ownerSession))
                    .andReturn().getResponse().getContentAsString();

            long id = objectMapper.readTree(createResponse).get("data").asLong();

            // 其他用户尝试删除
            MockHttpSession otherSession = loginWithRole("other_del_" + System.currentTimeMillis(), "user");
            String deleteJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/interfaceInfo/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deleteJson)
                            .session(otherSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));

            // 验证未被删除
            assertNotNull(interfaceInfoService.getById(id));
        }

        @Test
        @DisplayName("管理员删除他人接口成功")
        void shouldAllowAdminToDelete() throws Exception {
            MockHttpSession userSession = loginWithRole("user_del_" + System.currentTimeMillis(), "user");
            InterfaceInfoAddRequest addRequest = buildAddRequest("adminDelApi", "/api/admin_del", "GET");

            String createResponse = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(userSession))
                    .andReturn().getResponse().getContentAsString();

            long id = objectMapper.readTree(createResponse).get("data").asLong();

            // 管理员删除
            MockHttpSession adminSession = loginWithRole("admin_del_" + System.currentTimeMillis(), "admin");
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
            MockHttpSession session = loginAsUser();
            String deleteJson = "{\"id\":0}";

            mockMvc.perform(post("/interfaceInfo/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deleteJson)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }

    @Nested
    @DisplayName("GET /interfaceInfo/list 管理员查询列表")
    class ListTests {

        @Test
        @DisplayName("管理员查询接口列表成功")
        void shouldAllowAdminToList() throws Exception {
            MockHttpSession session = loginAsAdmin();

            mockMvc.perform(get("/interfaceInfo/list").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("非管理员查询返回无权限")
        void shouldDenyNormalUser() throws Exception {
            MockHttpSession session = loginWithRole("user_list_" + System.currentTimeMillis(), "user");

            mockMvc.perform(get("/interfaceInfo/list").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));
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
    }

    @Nested
    @DisplayName("POST /interfaceInfo/online 发布接口")
    class OnlineTests {

        @Test
        @DisplayName("管理员发布 OFFLINE 接口，状态变为 PUBLISHING（验证会失败回滚到 OFFLINE）")
        void shouldStartPublishingFromOffline() throws Exception {
            MockHttpSession adminSession = loginAsAdmin();
            MockHttpSession userSession = loginWithRole("user_online_" + System.currentTimeMillis(), "user");

            // 创建接口（状态为 OFFLINE）
            InterfaceInfoAddRequest addRequest = buildAddRequest("onlineApi", "/api/online_test", "GET");
            MvcResult createResult = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(userSession))
                    .andReturn();

            long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

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
            MockHttpSession userSession = loginWithRole("user_online2_" + System.currentTimeMillis(), "user");

            InterfaceInfoAddRequest addRequest = buildAddRequest("onlineApi2", "/api/online_test2", "GET");
            MvcResult createResult = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(userSession))
                    .andReturn();

            long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

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
            MockHttpSession userSession = loginWithRole("user_online3_" + System.currentTimeMillis(), "user");

            InterfaceInfoAddRequest addRequest = buildAddRequest("onlineApi3", "/api/online_test3", "GET");
            MvcResult createResult = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(userSession))
                    .andReturn();

            long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

            // 直接改为 ONLINE
            InterfaceInfo info = interfaceInfoService.getById(id);
            info.setStatus(InterfaceInfoStatusEnum.ONLINE.getValue());
            interfaceInfoService.updateById(info);

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
            MockHttpSession userSession = loginWithRole("user_offline_" + System.currentTimeMillis(), "user");

            // 创建接口
            InterfaceInfoAddRequest addRequest = buildAddRequest("offlineApi", "/api/offline_test", "GET");
            MvcResult createResult = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(userSession))
                    .andReturn();

            long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

            // 手动改为 ONLINE
            InterfaceInfo info = interfaceInfoService.getById(id);
            info.setStatus(InterfaceInfoStatusEnum.ONLINE.getValue());
            interfaceInfoService.updateById(info);

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
            MockHttpSession userSession = loginWithRole("user_offline2_" + System.currentTimeMillis(), "user");

            InterfaceInfoAddRequest addRequest = buildAddRequest("offlineApi2", "/api/offline_test2", "GET");
            MvcResult createResult = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(userSession))
                    .andReturn();

            long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

            // 手动改为 ONLINE
            InterfaceInfo info = interfaceInfoService.getById(id);
            info.setStatus(InterfaceInfoStatusEnum.ONLINE.getValue());
            interfaceInfoService.updateById(info);

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
            MockHttpSession userSession = loginWithRole("user_offline3_" + System.currentTimeMillis(), "user");

            InterfaceInfoAddRequest addRequest = buildAddRequest("offlineApi3", "/api/offline_test3", "GET");
            MvcResult createResult = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(userSession))
                    .andReturn();

            long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("data").asLong();

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
            InterfaceInfoAddRequest addRequest = buildAddRequest("offlineApi", "/api/offline_invoke", "GET");

            String createResponse = mockMvc.perform(post("/interfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(addRequest))
                            .session(session))
                    .andReturn().getResponse().getContentAsString();

            long id = objectMapper.readTree(createResponse).get("data").asLong();

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
    }
}
