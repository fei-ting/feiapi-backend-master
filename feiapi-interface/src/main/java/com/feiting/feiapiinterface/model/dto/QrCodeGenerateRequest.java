package com.feiting.feiapiinterface.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 二维码生成请求 DTO
 *
 * @author feiting
 */
@Data
public class QrCodeGenerateRequest implements Serializable {

    /**
     * 二维码内容（必填）
     * 支持文本、URL 等任意字符串，最多 1024 个字符且最多 1024 个 UTF-8 字节。
     */
    @NotBlank(message = "内容不能为空")
    @Size(min = 1, max = 1024, message = "内容字符长度必须在 1-1024 之间")
    private String content;

    /**
     * 二维码宽度（像素）
     * 默认 300，范围 100-1000
     */
    @NotNull(message = "宽度不能为空")
    @Min(value = 100, message = "宽度不能小于 100 像素")
    @Max(value = 1000, message = "宽度不能大于 1000 像素")
    private Integer width;

    /**
     * 二维码高度（像素）
     * 默认 300，范围 100-1000
     */
    @NotNull(message = "高度不能为空")
    @Min(value = 100, message = "高度不能小于 100 像素")
    @Max(value = 1000, message = "高度不能大于 1000 像素")
    private Integer height;

    @java.io.Serial
    private static final long serialVersionUID = 1L;
}
