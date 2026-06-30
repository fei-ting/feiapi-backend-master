package com.feiting.feiapi.model.dto.interfacequotaconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 接口配额类型配置更新请求。
 */
@Data
public class InterfaceQuotaConfigUpdateRequest implements Serializable {

    /**
     * 配额类型。
     */
    @NotBlank(message = "配额类型不能为空")
    @Size(max = 32, message = "配额类型长度不能超过 32")
    private String quotaType;

    /**
     * 初始发放额度。
     */
    @NotNull(message = "初始额度不能为空")
    @Positive(message = "初始额度必须大于 0")
    private Integer initialQuota;

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;
}
