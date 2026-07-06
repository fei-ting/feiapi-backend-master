package com.feiting.feiapiinterface.controller;

import com.feiting.feiapiinterface.model.dto.QrCodeGenerateRequest;
import com.feiting.feiapiinterface.model.vo.QrCodeGenerateVO;
import com.feiting.feiapiinterface.service.QrCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 二维码生成控制器测试
 *
 * @author feiting
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QrCodeController 单元测试")
class QrCodeControllerTest {

    @Mock
    private QrCodeService qrCodeService;

    @InjectMocks
    private QrCodeController qrCodeController;

    private QrCodeGenerateRequest validRequest;
    private QrCodeGenerateVO mockResponse;

    @BeforeEach
    void setUp() {
        // 初始化有效请求参数
        validRequest = new QrCodeGenerateRequest();
        validRequest.setContent("https://example.com");
        validRequest.setWidth(300);
        validRequest.setHeight(300);

        // 初始化模拟响应
        mockResponse = QrCodeGenerateVO.builder()
                .imageType("png")
                .width(300)
                .height(300)
                .base64("mockBase64Data")
                .dataUri("data:image/png;base64,mockBase64Data")
                .build();
    }

    @Test
    @DisplayName("生成二维码 - 正常场景")
    void generateQrCode_ValidRequest_Success() {
        // Arrange
        when(qrCodeService.generateQrCode(any(QrCodeGenerateRequest.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<QrCodeGenerateVO> response = qrCodeController.generateQrCode(validRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getImageType()).isEqualTo("png");
        assertThat(response.getBody().getWidth()).isEqualTo(300);
        assertThat(response.getBody().getHeight()).isEqualTo(300);
        assertThat(response.getBody().getBase64()).isEqualTo("mockBase64Data");
        assertThat(response.getBody().getDataUri()).startsWith("data:image/png;base64,");
    }

    @Test
    @DisplayName("生成二维码 - 返回响应状态码 200")
    void generateQrCode_ValidRequest_Returns200() {
        // Arrange
        when(qrCodeService.generateQrCode(any(QrCodeGenerateRequest.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<QrCodeGenerateVO> response = qrCodeController.generateQrCode(validRequest);

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("生成二维码 - 响应包含所有必要字段")
    void generateQrCode_ValidRequest_ResponseContainsAllFields() {
        // Arrange
        when(qrCodeService.generateQrCode(any(QrCodeGenerateRequest.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<QrCodeGenerateVO> response = qrCodeController.generateQrCode(validRequest);

        // Assert
        QrCodeGenerateVO body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getImageType()).isNotNull();
        assertThat(body.getWidth()).isNotNull();
        assertThat(body.getHeight()).isNotNull();
        assertThat(body.getBase64()).isNotNull();
        assertThat(body.getDataUri()).isNotNull();
    }
}
