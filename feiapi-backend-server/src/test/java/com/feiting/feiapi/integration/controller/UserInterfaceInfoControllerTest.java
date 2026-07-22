package com.feiting.feiapi.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.model.dto.user.UserLoginRequest;
import com.feiting.feiapi.constant.UserConstant;
import com.feiting.feiapi.controller.UserInterfaceInfoController;
import com.feiting.feiapi.model.dto.userinterfaceinfo.UserInterfaceInfoQueryRequest;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserInterfaceInfoController 集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("UserInterfaceInfoController 集成测试")
class UserInterfaceInfoControllerTest {

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
    private UserInterfaceInfoController userInterfaceInfoController;

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

    private MockHttpSession loginAsUser(String account) throws Exception {
        return loginWithRole(account, UserConstant.DEFAULT_ROLE);
    }

    private MockHttpSession loginAsAdmin(String account) throws Exception {
        return loginWithRole(account, UserConstant.ADMIN_ROLE);
    }

    /**
     * 创建一个接口并返回其 id
     */
    private long createInterface(String name, String path) {
        InterfaceInfo info = new InterfaceInfo();
        info.setName(name);
        info.setDescription("测试接口");
        info.setPath(path);
        info.setTargetHost(TEST_TARGET_HOST);
        info.setUrl(TEST_TARGET_HOST + path);
        info.setRequestHeader("{\"Content-Type\":\"application/json\"}");
        info.setResponseHeader("{\"Content-Type\":\"application/json\"}");
        info.setMethod("GET");
        info.setStatus(0);
        info.setUserId(1L);
        info.setIsDelete(0);
        interfaceInfoService.save(info);
        return info.getId();
    }

    /**
     * 创建一条用户接口调用关系并返回其 id
     */
    private long createUserInterfaceInfo(long userId, long interfaceInfoId, int leftNum, int totalNum, int status) {
        UserInterfaceInfo info = new UserInterfaceInfo();
        info.setUserId(userId);
        info.setInterfaceInfoId(interfaceInfoId);
        info.setLeftNum(leftNum);
        info.setTotalNum(totalNum);
        info.setStatus(status);
        info.setIsDelete(0);
        userInterfaceInfoService.save(info);
        return info.getId();
    }

    @Nested
    @DisplayName("POST /userInterfaceInfo/add 创建调用关系")
    class AddTests {

        @Test
        @DisplayName("普通用户新增调用关系失败")
        void shouldFailWhenNormalUserAdd() throws Exception {
            MockHttpSession session = loginAsUser("uaa01");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "uaa01").one();
            long interfaceInfoId = createInterface("addApi", "/api/add_01");

            String json = "{\"userId\":" + user.getId()
                    + ",\"interfaceInfoId\":" + interfaceInfoId
                    + ",\"totalNum\":0,\"leftNum\":100,\"status\":0}";

            mockMvc.perform(post("/userInterfaceInfo/add")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));

            UserInterfaceInfo saved = userInterfaceInfoService.lambdaQuery()
                    .eq(UserInterfaceInfo::getUserId, user.getId())
                    .eq(UserInterfaceInfo::getInterfaceInfoId, interfaceInfoId)
                    .one();
            assertNull(saved);
        }

        @Test
        @DisplayName("管理员新增成功且忽略调用次数和状态字段")
        void shouldAddByAdminAndIgnoreQuotaFields() throws Exception {
            MockHttpSession session = loginAsAdmin("uba01");
            loginAsUser("ubt01");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "ubt01").one();
            long interfaceInfoId = createInterface("addAdminApi", "/api/add_admin_01");

            String json = "{\"userId\":" + user.getId()
                    + ",\"interfaceInfoId\":" + interfaceInfoId
                    + ",\"totalNum\":999,\"leftNum\":888,\"status\":1}";

            MvcResult result = mockMvc.perform(post("/userInterfaceInfo/add")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isNumber())
                    .andReturn();

            long newId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("data").asLong();
            assertTrue(newId > 0);

            UserInterfaceInfo saved = userInterfaceInfoService.getById(newId);
            assertNotNull(saved);
            assertEquals(user.getId(), saved.getUserId());
            assertEquals(interfaceInfoId, saved.getInterfaceInfoId());
            assertEquals(0, saved.getTotalNum());
            assertEquals(0, saved.getLeftNum());
            assertEquals(0, saved.getStatus());
        }

        @Test
        @DisplayName("请求体为空返回参数错误")
        void shouldFailWhenBodyNull() throws Exception {
            MockHttpSession session = loginAsUser("uba02");

            mockMvc.perform(post("/userInterfaceInfo/add")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }

    @Nested
    @DisplayName("GET /userInterfaceInfo/my/get 和 /admin/get 获取详情")
    class GetByIdTests {

        @Test
        @DisplayName("普通用户根据 id 获取自己的调用关系成功")
        void shouldGetOwnRecordById() throws Exception {
            MockHttpSession session = loginAsUser("uga01");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "uga01").one();
            long interfaceInfoId = createInterface("getApi", "/api/get_01");
            long id = createUserInterfaceInfo(user.getId(), interfaceInfoId, 0, 0, 0);

            // 查询
            mockMvc.perform(get("/userInterfaceInfo/my/get")
                            .param("id", String.valueOf(id))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.userId").value(user.getId()))
                    .andExpect(jsonPath("$.data.interfaceInfoId").value(interfaceInfoId));
        }

        @Test
        @DisplayName("普通用户根据 id 获取他人的调用关系失败")
        void shouldFailWhenGetOtherUserRecordById() throws Exception {
            MockHttpSession ownerSession = loginAsUser("ugo01");
            assertNotNull(ownerSession);
            User owner = userService.lambdaQuery().eq(User::getUserAccount, "ugo01").one();
            long interfaceInfoId = createInterface("getOtherApi", "/api/get_other_01");
            long id = createUserInterfaceInfo(owner.getId(), interfaceInfoId, 0, 0, 0);

            MockHttpSession otherSession = loginAsUser("ugr01");
            mockMvc.perform(get("/userInterfaceInfo/my/get")
                            .param("id", String.valueOf(id))
                            .session(otherSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));
        }

        @Test
        @DisplayName("管理员根据 id 获取任意调用关系成功")
        void shouldAdminGetRecordById() throws Exception {
            MockHttpSession adminSession = loginAsAdmin("ugb01");
            loginAsUser("ugt01");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "ugt01").one();
            long interfaceInfoId = createInterface("getAdminApi", "/api/get_admin_01");
            long id = createUserInterfaceInfo(user.getId(), interfaceInfoId, 0, 0, 0);

            mockMvc.perform(get("/userInterfaceInfo/admin/get")
                            .param("id", String.valueOf(id))
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.userId").value(user.getId()))
                    .andExpect(jsonPath("$.data.interfaceInfoId").value(interfaceInfoId));
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            MockHttpSession session = loginAsUser("uga02");
            mockMvc.perform(get("/userInterfaceInfo/my/get")
                            .param("id", "0")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("不存在的 id 返回 null data")
        void shouldReturnNullForNonExistentId() throws Exception {
            MockHttpSession session = loginAsUser("uga03");
            mockMvc.perform(get("/userInterfaceInfo/my/get")
                            .param("id", "99999")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Nested
    @DisplayName("POST /userInterfaceInfo/delete 删除调用关系")
    class DeleteTests {

        @Test
        @DisplayName("普通用户删除自己的调用关系失败")
        void shouldFailWhenNormalUserDeleteOwnRecord() throws Exception {
            MockHttpSession session = loginAsUser("uda01");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "uda01").one();
            long interfaceInfoId = createInterface("delApi", "/api/del_01");
            long id = createUserInterfaceInfo(user.getId(), interfaceInfoId, 0, 0, 0);

            // 删除
            String deleteJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/userInterfaceInfo/delete")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deleteJson)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));

            assertNotNull(userInterfaceInfoService.getById(id), "普通用户无权删除，记录应仍然存在");
        }

        @Test
        @DisplayName("管理员删除调用关系成功")
        void shouldAdminDeleteRecord() throws Exception {
            MockHttpSession adminSession = loginAsAdmin("udb01");
            loginAsUser("udt01");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "udt01").one();
            long interfaceInfoId = createInterface("delAdminApi", "/api/del_admin_01");
            long id = createUserInterfaceInfo(user.getId(), interfaceInfoId, 0, 0, 0);

            String deleteJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/userInterfaceInfo/delete")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deleteJson)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            UserInterfaceInfo deleted = userInterfaceInfoService.getById(id);
            assertNull(deleted, "逻辑删除后应查不到");
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            MockHttpSession session = loginAsAdmin("uda02");
            String json = "{\"id\":0}";

            mockMvc.perform(post("/userInterfaceInfo/delete")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("记录不存在返回数据不存在")
        void shouldFailWhenNotFound() throws Exception {
            MockHttpSession session = loginAsAdmin("uda03");
            String json = "{\"id\":99999}";

            mockMvc.perform(post("/userInterfaceInfo/delete")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40400));
        }

        @Test
        @DisplayName("普通用户删除他人调用关系失败")
        void shouldFailWhenNormalUserDeleteOtherRecord() throws Exception {
            // 用户1创建
            MockHttpSession session1 = loginAsUser("udo01");
            User user1 = userService.lambdaQuery().eq(User::getUserAccount, "udo01").one();
            long interfaceInfoId = createInterface("delApi2", "/api/del_04");
            long id = createUserInterfaceInfo(user1.getId(), interfaceInfoId, 0, 0, 0);

            // 用户2尝试删除
            MockHttpSession session2 = loginAsUser("udr01");
            String deleteJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/userInterfaceInfo/delete")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deleteJson)
                            .session(session2))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));

            // 验证记录仍然存在
            assertNotNull(userInterfaceInfoService.getById(id));
        }
    }

    @Nested
    @DisplayName("POST /userInterfaceInfo/update 更新调用关系")
    class UpdateTests {

        @Test
        @DisplayName("普通用户更新自己的调用关系失败")
        void shouldFailWhenNormalUserUpdateOwnRecord() throws Exception {
            MockHttpSession session = loginAsUser("upa01");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "upa01").one();
            long interfaceInfoId = createInterface("updApi", "/api/upd_01");
            long id = createUserInterfaceInfo(user.getId(), interfaceInfoId, 10, 2, 0);

            String updateJson = "{\"id\":" + id + ",\"leftNum\":50,\"totalNum\":99,\"status\":1}";
            mockMvc.perform(post("/userInterfaceInfo/update")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));

            UserInterfaceInfo updated = userInterfaceInfoService.getById(id);
            assertEquals(10, updated.getLeftNum());
            assertEquals(2, updated.getTotalNum());
            assertEquals(0, updated.getStatus());
        }

        @Test
        @DisplayName("管理员更新时忽略调用次数和状态字段")
        void shouldIgnoreQuotaFieldsWhenAdminUpdate() throws Exception {
            MockHttpSession adminSession = loginAsAdmin("upb01");
            loginAsUser("upt01");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "upt01").one();
            long oldInterfaceInfoId = createInterface("updAdminApiOld", "/api/upd_admin_old_01");
            long newInterfaceInfoId = createInterface("updAdminApiNew", "/api/upd_admin_new_01");
            long id = createUserInterfaceInfo(user.getId(), oldInterfaceInfoId, 10, 2, 0);

            String updateJson = "{\"id\":" + id
                    + ",\"interfaceInfoId\":" + newInterfaceInfoId
                    + ",\"leftNum\":50,\"totalNum\":99,\"status\":1}";
            mockMvc.perform(post("/userInterfaceInfo/update")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson)
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            UserInterfaceInfo updated = userInterfaceInfoService.getById(id);
            assertEquals(newInterfaceInfoId, updated.getInterfaceInfoId());
            assertEquals(10, updated.getLeftNum());
            assertEquals(2, updated.getTotalNum());
            assertEquals(0, updated.getStatus());
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            MockHttpSession session = loginAsAdmin("upa02");
            String json = "{\"id\":0}";

            mockMvc.perform(post("/userInterfaceInfo/update")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("记录不存在返回数据不存在")
        void shouldFailWhenNotFound() throws Exception {
            MockHttpSession session = loginAsAdmin("upa03");
            String json = "{\"id\":99999,\"leftNum\":50}";

            mockMvc.perform(post("/userInterfaceInfo/update")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40400));
        }
    }

    @Nested
    @DisplayName("GET /userInterfaceInfo/my/list/page 和 /admin/list/page 分页查询")
    class ListByPageTests {

        @Test
        @DisplayName("普通用户无参数时使用默认分页返回自己的调用关系")
        void shouldReturnMyRecordsWithDefaults() throws Exception {
            MockHttpSession session = loginAsUser("upd01");
            mockMvc.perform(get("/userInterfaceInfo/my/list/page").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("pageSize > 50 返回参数错误")
        void shouldFailWhenPageSizeTooLarge() throws Exception {
            MockHttpSession session = loginAsUser("ups01");
            mockMvc.perform(get("/userInterfaceInfo/my/list/page")
                            .param("pageSize", "51")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("普通用户分页查询只返回自己的调用关系")
        void shouldReturnOnlyMyPaginatedData() throws Exception {
            MockHttpSession session = loginAsUser("up001");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "up001").one();
            long interfaceInfoId = createInterface("pageApi", "/api/page_01");
            createUserInterfaceInfo(user.getId(), interfaceInfoId, 0, 0, 0);
            loginAsUser("upo01");
            User otherUser = userService.lambdaQuery().eq(User::getUserAccount, "upo01").one();
            long otherInterfaceInfoId = createInterface("pageOtherApi", "/api/page_other_01");
            createUserInterfaceInfo(otherUser.getId(), otherInterfaceInfoId, 0, 0, 0);

            // 分页查询
            mockMvc.perform(get("/userInterfaceInfo/my/list/page")
                            .param("current", "1")
                            .param("pageSize", "10")
                            .param("userId", String.valueOf(otherUser.getId()))
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.records").isArray())
                    .andExpect(jsonPath("$.data.records[0].userId").value(user.getId()));
        }

        @Test
        @DisplayName("管理员分页查询可返回任意用户调用关系")
        void shouldAdminReturnPaginatedData() throws Exception {
            MockHttpSession adminSession = loginAsAdmin("upb01");
            loginAsUser("upt02");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "upt02").one();
            long interfaceInfoId = createInterface("pageAdminApi", "/api/page_admin_01");
            createUserInterfaceInfo(user.getId(), interfaceInfoId, 0, 0, 0);

            mockMvc.perform(get("/userInterfaceInfo/admin/list/page")
                            .param("current", "1")
                            .param("pageSize", "10")
                            .param("userId", String.valueOf(user.getId()))
                            .session(adminSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.records").isArray())
                    .andExpect(jsonPath("$.data.records[0].userId").value(user.getId()));
        }

        @Test
        @DisplayName("sortOrder 为 null 时管理员分页查询不触发空指针")
        void shouldNotThrowWhenSortOrderNull() throws Exception {
            UserInterfaceInfoQueryRequest queryRequest = new UserInterfaceInfoQueryRequest();
            queryRequest.setSortField("createTime");
            queryRequest.setSortOrder(null);

            UserInterfaceInfoController targetController = AopTestUtils.getTargetObject(userInterfaceInfoController);
            BaseResponse<Page<UserInterfaceInfo>> response = targetController.listUserInterfaceInfoByPage(queryRequest);

            assertEquals(0, response.getCode());
            assertNotNull(response.getData());
        }

        @Test
        @DisplayName("普通用户访问管理员分页接口失败")
        void shouldFailWhenNormalUserAccessAdminPage() throws Exception {
            MockHttpSession session = loginAsUser("upn01");

            mockMvc.perform(get("/userInterfaceInfo/admin/list/page")
                            .param("current", "1")
                            .param("pageSize", "10")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));
        }
    }
}
