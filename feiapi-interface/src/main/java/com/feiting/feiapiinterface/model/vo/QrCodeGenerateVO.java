package com.feiting.feiapiinterface.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 二维码生成响应 VO
 *
 * @author feiting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrCodeGenerateVO implements Serializable {

    /**
     * 图片类型（如 png、jpeg）
     */
    private String imageType;

    /**
     * 二维码实际宽度（像素）
     */
    private Integer width;

    /**
     * 二维码实际高度（像素）
     */
    private Integer height;

    /**
     * Base64 编码的二维码图片数据
     */
    private String base64;

    /**
     * Data URI 格式的二维码图片
     * 格式：data:image/png;base64,...
     * 可直接用于前端 img 标签的 src 属性
     */
    private String dataUri;

    @java.io.Serial
    private static final long serialVersionUID = 1L;
}
