package com.feiting.feiapiinterface.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 名称测试接口控制器单元测试。
 */
@DisplayName("NameController 单元测试")
class NameControllerTest {

    /**
     * MVC 测试客户端。
     */
    private MockMvc mockMvc;

    /**
     * 初始化轻量 MVC 测试客户端。
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NameController()).build();
    }

    /**
     * 校验合法 username 能正常返回用户名。
     */
    @Test
    @DisplayName("POST /name/user 输入 username 时返回用户名")
    void shouldReturnUsernameWhenUsernamePresent() throws Exception {
        String responseBody = mockMvc.perform(post("/name/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"FeiAPI\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(responseBody).isEqualTo("Post 用户名字是FeiAPI");
    }

    /**
     * 校验缺少 username 时返回 400，避免在线调试误判为空用户名调用成功。
     */
    @Test
    @DisplayName("POST /name/user 缺少 username 时返回 400")
    void shouldReturnBadRequestWhenUsernameMissing() throws Exception {
        String responseBody = mockMvc.perform(post("/name/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ip\":\"8.8.8.8\"}"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(responseBody).isEqualTo("username 不能为空");
    }
}
