package com.feiting.feiapi.model.dto.userinterfaceinfo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户接口调用关系更新请求
 */
@Data
public class UserInterfaceInfoUpdateRequest implements Serializable {

    /**
     * 主键
     */
    @NotNull(message = "主键不能为空")
    @Positive(message = "主键必须大于 0")
    private Long id;

    /**
     * 调用人 id
     */
    @Positive(message = "调用人 id 必须大于 0")
    private Long userId;

    /**
     * 接口 id
     */
    @Positive(message = "接口 id 必须大于 0")
    private Long interfaceInfoId;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}
