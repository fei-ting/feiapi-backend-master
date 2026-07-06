package com.feiting.feiapiinterface.service;

import com.feiting.feiapiinterface.model.dto.QrCodeGenerateRequest;
import com.feiting.feiapiinterface.model.vo.QrCodeGenerateVO;
import com.feiting.feiapiinterface.service.impl.QrCodeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 二维码生成服务实现类测试
 *
 * @author feiting
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QrCodeServiceImpl 单元测试")
class QrCodeServiceImplTest {

    @InjectMocks
    private QrCodeServiceImpl qrCodeService;

    private QrCodeGenerateRequest validRequest;

    @BeforeEach
    void setUp() {
        // 初始化有效请求参数
        validRequest = new QrCodeGenerateRequest();
        validRequest.setContent("https://example.com");
        validRequest.setWidth(300);
        validRequest.setHeight(300);
    }

    @Test
    @DisplayName("生成二维码 - 正常场景")
    void generateQrCode_ValidRequest_Success() {
        // Act
        QrCodeGenerateVO result = qrCodeService.generateQrCode(validRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getImageType()).isEqualTo("png");
        assertThat(result.getWidth()).isEqualTo(300);
        assertThat(result.getHeight()).isEqualTo(300);
        assertThat(result.getBase64()).isNotBlank();
        assertThat(result.getDataUri()).startsWith("data:image/png;base64,");
    }

    @Test
    @DisplayName("生成二维码 - 自定义尺寸")
    void generateQrCode_CustomSize_Success() {
        // Arrange
        validRequest.setWidth(500);
        validRequest.setHeight(500);

        // Act
        QrCodeGenerateVO result = qrCodeService.generateQrCode(validRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isEqualTo(500);
        assertThat(result.getHeight()).isEqualTo(500);
        assertThat(result.getBase64()).isNotBlank();
    }

    @Test
    @DisplayName("生成二维码 - 最小尺寸")
    void generateQrCode_MinSize_Success() {
        // Arrange
        validRequest.setWidth(100);
        validRequest.setHeight(100);

        // Act
        QrCodeGenerateVO result = qrCodeService.generateQrCode(validRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isEqualTo(100);
        assertThat(result.getHeight()).isEqualTo(100);
    }

    @Test
    @DisplayName("生成二维码 - 最大尺寸")
    void generateQrCode_MaxSize_Success() {
        // Arrange
        validRequest.setWidth(1000);
        validRequest.setHeight(1000);

        // Act
        QrCodeGenerateVO result = qrCodeService.generateQrCode(validRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isEqualTo(1000);
        assertThat(result.getHeight()).isEqualTo(1000);
    }

    @Test
    @DisplayName("生成二维码 - 中文内容")
    void generateQrCode_ChineseContent_Success() {
        // Arrange
        validRequest.setContent("你好，世界！");

        // Act
        QrCodeGenerateVO result = qrCodeService.generateQrCode(validRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getBase64()).isNotBlank();
    }

    @Test
    @DisplayName("生成二维码 - 长文本内容")
    void generateQrCode_LongContent_Success() {
        // Arrange
        String longContent = "A".repeat(1024);
        validRequest.setContent(longContent);

        // Act
        QrCodeGenerateVO result = qrCodeService.generateQrCode(validRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getBase64()).isNotBlank();
    }

    @Test
    @DisplayName("生成二维码 - UTF-8 字节长度超过限制应抛出异常")
    void generateQrCode_ContentBytesTooLong_ShouldThrowException() {
        // Arrange
        validRequest.setContent("中".repeat(342));

        // Act & Assert
        assertThatThrownBy(() -> qrCodeService.generateQrCode(validRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("UTF-8 字节长度不能超过 1024");
    }

    @Test
    @DisplayName("生成二维码 - 内容为空应抛出异常")
    void generateQrCode_EmptyContent_ShouldThrowException() {
        // Arrange
        validRequest.setContent("");

        // Act & Assert
        assertThatThrownBy(() -> qrCodeService.generateQrCode(validRequest))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("生成二维码 - Base64 编码有效")
    void generateQrCode_Base64Valid_Success() {
        // Act
        QrCodeGenerateVO result = qrCodeService.generateQrCode(validRequest);

        // Assert
        assertThat(result.getBase64()).isNotBlank();
        // 验证 Base64 可以解码
        assertThat(result.getBase64()).matches("[A-Za-z0-9+/]*={0,2}");
    }

    @Test
    @DisplayName("生成二维码 - Data URI 格式正确")
    void generateQrCode_DataUriFormat_Success() {
        // Act
        QrCodeGenerateVO result = qrCodeService.generateQrCode(validRequest);

        // Assert
        assertThat(result.getDataUri()).startsWith("data:image/png;base64,");
        assertThat(result.getDataUri()).contains(result.getBase64());
    }
}
