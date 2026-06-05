package com.feiting.feiapi.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiting.feiapi.model.dto.interfaceInfo.InterfaceInfoAddRequest;
import com.feiting.feiapi.model.dto.user.UserLoginRequest;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
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

    private MockHttpSession loginAsUser(String account) throws Exception {
        userService.userRegister(account, "password123", "password123");

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
     * 创建一个接口并返回其 id
     */
    private long createInterface(String name, String url) {
        InterfaceInfo info = new InterfaceInfo();
        info.setName(name);
        info.setDescription("测试接口");
        info.setUrl(url);
        info.setRequestHeader("{\"Content-Type\":\"application/json\"}");
        info.setResponseHeader("{\"Content-Type\":\"application/json\"}");
        info.setMethod("GET");
        info.setStatus(0);
        info.setUserId(1L);
        info.setIsDelete(0);
        interfaceInfoService.save(info);
        return info.getId();
    }

    @Nested
    @DisplayName("POST /userInterfaceInfo/add 创建调用关系")
    class AddTests {

        @Test
        @DisplayName("已登录用户创建成功，返回新记录 id")
        void shouldAddSuccessfully() throws Exception {
            MockHttpSession session = loginAsUser("uii_add_01");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "uii_add_01").one();
            long interfaceInfoId = createInterface("addApi", "/api/add_01");

            String json = "{\"userId\":" + user.getId()
                    + ",\"interfaceInfoId\":" + interfaceInfoId
                    + ",\"totalNum\":0,\"leftNum\":100}";

            MvcResult result = mockMvc.perform(post("/userInterfaceInfo/add")
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

            // 验证数据库中确实存在
            UserInterfaceInfo saved = userInterfaceInfoService.getById(newId);
            assertNotNull(saved);
            assertEquals(user.getId(), saved.getUserId());
            assertEquals(interfaceInfoId, saved.getInterfaceInfoId());
        }

        @Test
        @DisplayName("请求体为空返回参数错误")
        void shouldFailWhenBodyNull() throws Exception {
            MockHttpSession session = loginAsUser("uii_add_02");

            mockMvc.perform(post("/userInterfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }

    @Nested
    @DisplayName("GET /userInterfaceInfo/get 获取详情")
    class GetByIdTests {

        @Test
        @DisplayName("根据 id 获取成功")
        void shouldGetById() throws Exception {
            MockHttpSession session = loginAsUser("uii_get_01");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "uii_get_01").one();
            long interfaceInfoId = createInterface("getApi", "/api/get_01");

            // 先创建
            String json = "{\"userId\":" + user.getId()
                    + ",\"interfaceInfoId\":" + interfaceInfoId + "}";
            MvcResult createResult = mockMvc.perform(post("/userInterfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk())
                    .andReturn();

            long id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("data").asLong();

            // 查询
            mockMvc.perform(get("/userInterfaceInfo/get").param("id", String.valueOf(id)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.userId").value(user.getId()))
                    .andExpect(jsonPath("$.data.interfaceInfoId").value(interfaceInfoId));
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            mockMvc.perform(get("/userInterfaceInfo/get").param("id", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("不存在的 id 返回 null data")
        void shouldReturnNullForNonExistentId() throws Exception {
            mockMvc.perform(get("/userInterfaceInfo/get").param("id", "99999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Nested
    @DisplayName("POST /userInterfaceInfo/delete 删除调用关系")
    class DeleteTests {

        @Test
        @DisplayName("本人删除成功")
        void shouldDeleteOwnRecord() throws Exception {
            MockHttpSession session = loginAsUser("uii_del_01");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "uii_del_01").one();
            long interfaceInfoId = createInterface("delApi", "/api/del_01");

            String json = "{\"userId\":" + user.getId()
                    + ",\"interfaceInfoId\":" + interfaceInfoId + "}";
            MvcResult createResult = mockMvc.perform(post("/userInterfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk())
                    .andReturn();

            long id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("data").asLong();

            // 删除
            String deleteJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/userInterfaceInfo/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(deleteJson)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            // 验证已被逻辑删除
            UserInterfaceInfo deleted = userInterfaceInfoService.getById(id);
            assertNull(deleted, "逻辑删除后应查不到");
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            MockHttpSession session = loginAsUser("uii_del_02");
            String json = "{\"id\":0}";

            mockMvc.perform(post("/userInterfaceInfo/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("记录不存在返回数据不存在")
        void shouldFailWhenNotFound() throws Exception {
            MockHttpSession session = loginAsUser("uii_del_03");
            String json = "{\"id\":99999}";

            mockMvc.perform(post("/userInterfaceInfo/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40400));
        }

        @Test
        @DisplayName("非本人且非管理员删除应失败")
        void shouldFailWhenNotOwner() throws Exception {
            // 用户1创建
            MockHttpSession session1 = loginAsUser("uii_del_owner");
            User user1 = userService.lambdaQuery().eq(User::getUserAccount, "uii_del_owner").one();
            long interfaceInfoId = createInterface("delApi2", "/api/del_04");

            String json = "{\"userId\":" + user1.getId()
                    + ",\"interfaceInfoId\":" + interfaceInfoId + "}";
            MvcResult createResult = mockMvc.perform(post("/userInterfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session1))
                    .andExpect(status().isOk())
                    .andReturn();

            long id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("data").asLong();

            // 用户2尝试删除
            MockHttpSession session2 = loginAsUser("uii_del_other");
            String deleteJson = "{\"id\":" + id + "}";
            mockMvc.perform(post("/userInterfaceInfo/delete")
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
        @DisplayName("本人更新成功")
        void shouldUpdateOwnRecord() throws Exception {
            MockHttpSession session = loginAsUser("uii_upd_01");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "uii_upd_01").one();
            long interfaceInfoId = createInterface("updApi", "/api/upd_01");

            String json = "{\"userId\":" + user.getId()
                    + ",\"interfaceInfoId\":" + interfaceInfoId + "}";
            MvcResult createResult = mockMvc.perform(post("/userInterfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk())
                    .andReturn();

            long id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("data").asLong();

            // 更新
            String updateJson = "{\"id\":" + id + ",\"leftNum\":50,\"status\":1}";
            mockMvc.perform(post("/userInterfaceInfo/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateJson)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            // 验证数据库已更新
            UserInterfaceInfo updated = userInterfaceInfoService.getById(id);
            assertEquals(50, updated.getLeftNum());
            assertEquals(1, updated.getStatus());
        }

        @Test
        @DisplayName("id <= 0 返回参数错误")
        void shouldFailWhenIdInvalid() throws Exception {
            MockHttpSession session = loginAsUser("uii_upd_02");
            String json = "{\"id\":0}";

            mockMvc.perform(post("/userInterfaceInfo/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("记录不存在返回数据不存在")
        void shouldFailWhenNotFound() throws Exception {
            MockHttpSession session = loginAsUser("uii_upd_03");
            String json = "{\"id\":99999,\"leftNum\":50}";

            mockMvc.perform(post("/userInterfaceInfo/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40400));
        }
    }

    @Nested
    @DisplayName("GET /userInterfaceInfo/list/page 分页查询")
    class ListByPageTests {

        @Test
        @DisplayName("无参数时使用默认分页返回成功")
        void shouldReturnSuccessWithDefaults() throws Exception {
            mockMvc.perform(get("/userInterfaceInfo/list/page"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("pageSize > 50 返回参数错误")
        void shouldFailWhenPageSizeTooLarge() throws Exception {
            mockMvc.perform(get("/userInterfaceInfo/list/page")
                            .param("pageSize", "51"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40000));
        }

        @Test
        @DisplayName("分页查询返回正确数据")
        void shouldReturnPaginatedData() throws Exception {
            MockHttpSession session = loginAsUser("uii_page_01");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "uii_page_01").one();
            long interfaceInfoId = createInterface("pageApi", "/api/page_01");

            // 创建一条记录
            String json = "{\"userId\":" + user.getId()
                    + ",\"interfaceInfoId\":" + interfaceInfoId + "}";
            mockMvc.perform(post("/userInterfaceInfo/add")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json)
                            .session(session))
                    .andExpect(status().isOk());

            // 分页查询
            mockMvc.perform(get("/userInterfaceInfo/list/page")
                            .param("current", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.records").isArray());
        }
    }
}
