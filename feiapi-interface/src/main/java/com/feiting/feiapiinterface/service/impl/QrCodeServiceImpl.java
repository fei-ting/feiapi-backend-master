package com.feiting.feiapiinterface.service.impl;

import com.feiting.feiapiinterface.model.dto.QrCodeGenerateRequest;
import com.feiting.feiapiinterface.model.vo.QrCodeGenerateVO;
import com.feiting.feiapiinterface.service.QrCodeService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 二维码生成服务实现类
 * 使用 ZXing 库生成二维码，支持自定义尺寸
 *
 * @author feiting
 */
@Slf4j
@Service
public class QrCodeServiceImpl implements QrCodeService {

    /**
     * 图片格式常量
     */
    private static final String IMAGE_FORMAT = "png";

    /**
     * Data URI 前缀
     */
    private static final String DATA_URI_PREFIX = "data:image/png;base64,";

    /**
     * 默认纠错级别（中等纠错能力，约 15%）
     */
    private static final ErrorCorrectionLevel DEFAULT_ERROR_CORRECTION = ErrorCorrectionLevel.M;

    /**
     * 默认边距（二维码与边框的距离，单位：模块数）
     */
    private static final int DEFAULT_MARGIN = 1;

    /**
     * 二维码内容最大 UTF-8 字节数。
     */
    private static final int MAX_CONTENT_BYTES = 1024;

    @Override
    public QrCodeGenerateVO generateQrCode(QrCodeGenerateRequest request) {
        String content = request.getContent();
        int width = request.getWidth();
        int height = request.getHeight();
        int contentByteLength = content.getBytes(StandardCharsets.UTF_8).length;
        if (contentByteLength > MAX_CONTENT_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "二维码内容 UTF-8 字节长度不能超过 1024");
        }

        log.info("开始生成二维码，内容字符数：{}，内容字节数：{}，尺寸：{}x{}",
                content.length(), contentByteLength, width, height);

        try {
            // 1. 生成 BitMatrix
            BitMatrix bitMatrix = generateBitMatrix(content, width, height);

            // 2. 将 BitMatrix 转换为 BufferedImage
            BufferedImage bufferedImage = convertToBufferedImage(bitMatrix, width, height);

            // 3. 将 BufferedImage 转换为 Base64
            String base64 = convertToBase64(bufferedImage);

            // 4. 构建 Data URI
            String dataUri = DATA_URI_PREFIX + base64;

            log.info("二维码生成成功，Base64 长度：{}", base64.length());

            // 5. 构建响应对象
            return QrCodeGenerateVO.builder()
                    .imageType(IMAGE_FORMAT)
                    .width(width)
                    .height(height)
                    .base64(base64)
                    .dataUri(dataUri)
                    .build();

        } catch (WriterException e) {
            log.warn("二维码生成失败，内容字符数：{}，内容字节数：{}，尺寸：{}x{}，原因：{}",
                    content.length(), contentByteLength, width, height, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "二维码内容无法生成图片，请缩短内容或调整参数", e);
        } catch (IOException e) {
            log.error("二维码图片转换失败", e);
            throw new RuntimeException("二维码图片转换失败：" + e.getMessage(), e);
        }
    }

    /**
     * 生成二维码的 BitMatrix
     *
     * @param content 二维码内容
     * @param width   宽度
     * @param height  高度
     * @return BitMatrix 对象
     * @throws WriterException 编码异常
     */
    private BitMatrix generateBitMatrix(String content, int width, int height) throws WriterException {
        // 配置编码参数
        Map<EncodeHintType, Object> hints = new HashMap<>();
        // 设置纠错级别
        hints.put(EncodeHintType.ERROR_CORRECTION, DEFAULT_ERROR_CORRECTION);
        // 设置字符编码
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        // 设置边距
        hints.put(EncodeHintType.MARGIN, DEFAULT_MARGIN);

        // 创建二维码写入器
        QRCodeWriter qrCodeWriter = new QRCodeWriter();

        // 生成 BitMatrix
        return qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
    }

    /**
     * 将 BitMatrix 转换为 BufferedImage
     * 自定义实现，不依赖 ZXing 的 javase 模块
     *
     * @param bitMatrix 二维码位矩阵
     * @param width     图片宽度
     * @param height    图片高度
     * @return BufferedImage 对象
     */
    private BufferedImage convertToBufferedImage(BitMatrix bitMatrix, int width, int height) {
        // 创建黑白图片
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // 获取 BitMatrix 的实际尺寸
        int matrixWidth = bitMatrix.getWidth();
        int matrixHeight = bitMatrix.getHeight();

        // 计算缩放比例
        double scaleX = (double) width / matrixWidth;
        double scaleY = (double) height / matrixHeight;

        // 遍历每个像素，设置颜色
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 计算对应的矩阵坐标
                int matrixX = (int) (x / scaleX);
                int matrixY = (int) (y / scaleY);

                // 判断该位置是否为黑色（true 表示黑色，false 表示白色）
                if (matrixX < matrixWidth && matrixY < matrixHeight && bitMatrix.get(matrixX, matrixY)) {
                    // 黑色
                    image.setRGB(x, y, 0x000000);
                } else {
                    // 白色
                    image.setRGB(x, y, 0xFFFFFF);
                }
            }
        }

        return image;
    }

    /**
     * 将 BufferedImage 转换为 Base64 字符串
     *
     * @param image BufferedImage 对象
     * @return Base64 编码字符串
     * @throws IOException IO 异常
     */
    private String convertToBase64(BufferedImage image) throws IOException {
        // 使用 ByteArrayOutputStream 缓存图片数据
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // 将图片写入输出流
        ImageIO.write(image, IMAGE_FORMAT, outputStream);

        // 转换为字节数组
        byte[] imageBytes = outputStream.toByteArray();

        // Base64 编码
        return Base64.getEncoder().encodeToString(imageBytes);
    }
}
