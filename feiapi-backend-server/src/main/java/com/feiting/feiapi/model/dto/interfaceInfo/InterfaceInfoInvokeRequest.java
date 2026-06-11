package com.feiting.feiapi.model.dto.interfaceInfo;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 测试接口请求
 *
 * @Author feiting
 */
@Data
public class InterfaceInfoInvokeRequest implements Serializable {

    /**
     * 主键
     */
    @NotNull(message = "接口 id 不能为空")
    @Positive(message = "接口 id 必须大于 0")
    private Long id;

    /**
     * 用户请求参数
     */
    @Size(max = 65535, message = "用户请求参数长度不能超过 65535")
    private String userRequestParams;

    private static final long serialVersionUID = 1L;
}
