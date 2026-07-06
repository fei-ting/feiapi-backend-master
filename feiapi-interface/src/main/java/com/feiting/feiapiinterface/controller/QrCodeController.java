package com.feiting.feiapiinterface.controller;

import com.feiting.feiapiinterface.model.dto.QrCodeGenerateRequest;
import com.feiting.feiapiinterface.model.vo.QrCodeGenerateVO;
import com.feiting.feiapiinterface.service.QrCodeService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 二维码生成接口控制器
 * 提供二维码生成功能，支持自定义内容和尺寸
 *
 * @author feiting
 */
@Slf4j
@RestController
@RequestMapping("/qrcode")
public class QrCodeController {

    @Resource
    private QrCodeService qrCodeService;

    /**
     * 生成二维码
     * 根据传入的内容和尺寸生成二维码图片，返回 Base64 编码和 Data URI
     *
     * @param request 二维码生成请求参数（包含 content、width、height）
     * @return 二维码生成结果（包含 imageType、width、height、base64、dataUri）
     */
    @PostMapping("/generate")
    public ResponseEntity<QrCodeGenerateVO> generateQrCode(
            @Valid @RequestBody QrCodeGenerateRequest request) {
        log.info("收到二维码生成请求，内容长度：{}，尺寸：{}x{}",
                request.getContent().length(), request.getWidth(), request.getHeight());

        // 调用服务生成二维码
        QrCodeGenerateVO response = qrCodeService.generateQrCode(request);

        log.info("二维码生成成功，返回响应");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }
}
