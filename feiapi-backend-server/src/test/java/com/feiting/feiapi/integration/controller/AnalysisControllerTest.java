package com.feiting.feiapi.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feiting.feiapi.model.dto.user.UserLoginRequest;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfaceInvokeLogService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.entity.InterfaceInvokeLog;
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

import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AnalysisController 集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AnalysisController 集成测试")
class AnalysisControllerTest {

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
    private InterfaceInvokeLogService interfaceInvokeLogService;

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

    private void insertInterfaceInfo(long id, String name, String path) {
        InterfaceInfo info = new InterfaceInfo();
        info.setId(id);
        info.setName(name);
        info.setDescription("desc_" + name);
        info.setPath(path);
        info.setTargetHost(TEST_TARGET_HOST);
        info.setUrl(TEST_TARGET_HOST + path);
        info.setRequestHeader("{}");
        info.setResponseHeader("{}");
        info.setMethod("GET");
        info.setStatus(1);
        info.setUserId(1L);
        info.setIsDelete(0);
        interfaceInfoService.save(info);
    }

    private void insertUserInterfaceInfo(long userId, long interfaceInfoId, int totalNum) {
        UserInterfaceInfo info = new UserInterfaceInfo();
        info.setUserId(userId);
        info.setInterfaceInfoId(interfaceInfoId);
        info.setTotalNum(totalNum);
        info.setLeftNum(100);
        info.setStatus(0);
        info.setIsDelete(0);
        userInterfaceInfoService.save(info);
    }

    /**
     * 插入接口调用日志。
     *
     * @param interfaceInfoId 接口 ID
     * @param statusCode      响应状态码
     * @param success         是否调用成功
     * @param responseTimeMs  响应耗时，单位毫秒
     * @param invokeTime      调用发生时间
     */
    private void insertInterfaceInvokeLog(long interfaceInfoId,
                                          int statusCode,
                                          boolean success,
                                          long responseTimeMs,
                                          Date invokeTime) {
        InterfaceInvokeLog interfaceInvokeLog = new InterfaceInvokeLog();
        interfaceInvokeLog.setUserId(1L);
        interfaceInvokeLog.setInterfaceInfoId(interfaceInfoId);
        interfaceInvokeLog.setPath("/api/home-stat");
        interfaceInvokeLog.setMethod("GET");
        interfaceInvokeLog.setStatusCode(statusCode);
        interfaceInvokeLog.setSuccess(success ? 1 : 0);
        interfaceInvokeLog.setResponseTimeMs(responseTimeMs);
        interfaceInvokeLog.setInvokeTime(invokeTime);
        interfaceInvokeLog.setIsDelete(0);
        interfaceInvokeLogService.save(interfaceInvokeLog);
    }

    /**
     * 获取昨天同一时刻。
     *
     * @return 昨天同一时刻
     */
    private Date getYesterday() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        return calendar.getTime();
    }

    @Nested
    @DisplayName("GET /analysis/home/stats")
    class HomeStatsTests {

        @Test
        @DisplayName("未登录用户可查询首页统计，无调用时累计运行指标为空")
        void shouldReturnHomeStatsWithoutLoginWhenNoInvokeLog() throws Exception {
            insertInterfaceInfo(1101L, "home_api", "/api/home");

            mockMvc.perform(get("/analysis/home/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.platformInterfaceCount").value(1))
                    .andExpect(jsonPath("$.data.totalInvocations").value(0))
                    .andExpect(jsonPath("$.data.successRate").value(nullValue()))
                    .andExpect(jsonPath("$.data.averageResponseTimeMs").value(nullValue()))
                    .andExpect(jsonPath("$.data.todayInvocations").doesNotExist())
                    .andExpect(jsonPath("$.data.availabilityRate").doesNotExist());
        }

        @Test
        @DisplayName("按全部调用日志计算首页运行指标")
        void shouldCalculateHomeStatsByAllInvokeLogs() throws Exception {
            insertInterfaceInfo(1201L, "home_stat_api", "/api/home-stat");
            insertInterfaceInvokeLog(1201L, 200, true, 100L, new Date());
            insertInterfaceInvokeLog(1201L, 500, false, 500L, new Date());
            insertInterfaceInvokeLog(1201L, 200, true, 300L, getYesterday());

            mockMvc.perform(get("/analysis/home/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.platformInterfaceCount").value(1))
                    .andExpect(jsonPath("$.data.totalInvocations").value(3))
                    .andExpect(jsonPath("$.data.successRate").value(66.7))
                    .andExpect(jsonPath("$.data.averageResponseTimeMs").value(300))
                    .andExpect(jsonPath("$.data.todayInvocations").doesNotExist())
                    .andExpect(jsonPath("$.data.availabilityRate").doesNotExist());
        }
    }

    @Nested
    @DisplayName("GET /analysis/top/interface/invoke")
    class TopInvokeTests {

        @Test
        @DisplayName("管理员查询 top 接口调用排行，验证排序正确")
        void shouldReturnTopInvokeInterfaceInfoSortedByTotalNum() throws Exception {
            MockHttpSession session = loginWithRole("ana01", "admin");
            User user = userService.lambdaQuery().eq(User::getUserAccount, "ana01").one();

            // 插入 3 个接口
            insertInterfaceInfo(1001L, "api_low", "/api/low");
            insertInterfaceInfo(1002L, "api_high", "/api/high");
            insertInterfaceInfo(1003L, "api_mid", "/api/mid");

            // 插入调用数据：high=300, mid=200, low=100
            insertUserInterfaceInfo(user.getId(), 1001L, 100);
            insertUserInterfaceInfo(user.getId(), 1002L, 300);
            insertUserInterfaceInfo(user.getId(), 1003L, 200);

            MvcResult result = mockMvc.perform(get("/analysis/top/interface/invoke").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].isDelete").doesNotExist())
                    .andReturn();

            // 验证返回了 3 条数据
            String content = result.getResponse().getContentAsString();
            com.fasterxml.jackson.databind.JsonNode data = objectMapper.readTree(content).get("data");
            assertTrue(data.size() >= 3, "应返回至少 3 条排行数据");

            // 按本测试插入的接口 id 精确断言（不受全局初始化数据影响）
            // 第一名: api_high (id=1002), totalNum=300
            assertEquals(1002, data.get(0).get("id").asLong(), "第一名应为 api_high");
            assertEquals("api_high", data.get(0).get("name").asText());
            assertEquals(300, data.get(0).get("totalNum").asInt());

            // 第二名: api_mid (id=1003), totalNum=200
            assertEquals(1003, data.get(1).get("id").asLong(), "第二名应为 api_mid");
            assertEquals("api_mid", data.get(1).get("name").asText());
            assertEquals(200, data.get(1).get("totalNum").asInt());

            // 第三名: api_low (id=1001), totalNum=100
            assertEquals(1001, data.get(2).get("id").asLong(), "第三名应为 api_low");
            assertEquals("api_low", data.get(2).get("name").asText());
            assertEquals(100, data.get(2).get("totalNum").asInt());
        }

        @Test
        @DisplayName("无调用数据时返回空列表")
        void shouldReturnEmptyListWhenNoData() throws Exception {
            MockHttpSession session = loginWithRole("ane01", "admin");

            mockMvc.perform(get("/analysis/top/interface/invoke").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("非管理员查询返回无权限")
        void shouldFailWhenNotAdmin() throws Exception {
            MockHttpSession session = loginWithRole("una01", "user");

            mockMvc.perform(get("/analysis/top/interface/invoke").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40101));
        }

        @Test
        @DisplayName("未登录查询返回未登录错误")
        void shouldFailWhenNotLoggedIn() throws Exception {
            mockMvc.perform(get("/analysis/top/interface/invoke"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40100));
        }
    }
}
