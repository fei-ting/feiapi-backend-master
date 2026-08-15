package com.feiting.feiapiinterface.exception;

import com.feiting.feiapiinterface.controller.QrCodeController;
import com.feiting.feiapiinterface.service.QrCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口服务全局异常处理器测试。
 */
@DisplayName("接口服务全局异常处理器测试")
class GlobalExceptionHandlerTest {

    /**
     * MVC 测试客户端。
     */
    private MockMvc mockMvc;

    /**
     * 二维码生成服务。
     */
    private QrCodeService qrCodeService;

    /**
     * 初始化带全局异常处理器的 MVC 测试客户端。
     */
    @BeforeEach
    void setUp() {
        qrCodeService = mock(QrCodeService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new QrCodeController(qrCodeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * 校验二维码宽度过小时返回 JSON 错误，而不是 Spring 默认 HTML 错误页。
     */
    @Test
    @DisplayName("二维码宽度过小时返回中文 JSON 错误")
    void shouldReturnJsonErrorWhenQrCodeWidthTooSmall() throws Exception {
        mockMvc.perform(post("/qrcode/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "sss",
                                  "width": 5,
                                  "height": 300
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("宽度不能小于 100 像素"))
                .andExpect(content().string(containsString("\"message\":\"宽度不能小于 100 像素\"")));
    }

    /**
     * 校验二维码内容为空时返回 DTO 中声明的中文错误。
     */
    @Test
    @DisplayName("二维码内容为空时返回中文 JSON 错误")
    void shouldReturnJsonErrorWhenQrCodeContentBlank() throws Exception {
        mockMvc.perform(post("/qrcode/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "",
                                  "width": 300,
                                  "height": 300
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("内容不能为空"));
    }

    /**
     * 校验请求体不是合法 JSON 时返回友好错误。
     */
    @Test
    @DisplayName("请求体格式错误时返回中文 JSON 错误")
    void shouldReturnJsonErrorWhenRequestBodyMalformed() throws Exception {
        mockMvc.perform(post("/qrcode/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{错误 JSON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("请求体格式不正确"));
    }

    /**
     * 校验接口主动抛出的业务拒绝异常会保留中文原因。
     */
    @Test
    @DisplayName("接口主动拒绝时返回中文 JSON 错误")
    void shouldReturnJsonErrorWhenResponseStatusExceptionRaised() throws Exception {
        when(qrCodeService.generateQrCode(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "二维码内容 UTF-8 字节长度不能超过 1024"));

        mockMvc.perform(post("/qrcode/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "sss",
                                  "width": 300,
                                  "height": 300
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("二维码内容 UTF-8 字节长度不能超过 1024"));
    }
}
