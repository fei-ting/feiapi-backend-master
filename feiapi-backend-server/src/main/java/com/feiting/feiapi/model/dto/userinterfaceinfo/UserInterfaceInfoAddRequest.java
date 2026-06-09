package com.feiting.feiapi.model.dto.userinterfaceinfo;

import com.baomidou.mybatisplus.annotation.TableField;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户接口调用关系创建请求
 */
@Data
public class UserInterfaceInfoAddRequest implements Serializable {

    /**
     * 调用人 id
     */
    @NotNull(message = "调用人 id 不能为空")
    @Positive(message = "调用人 id 必须大于 0")
    private Long userId;

    /**
     * 接口 id
     */
    @NotNull(message = "接口 id 不能为空")
    @Positive(message = "接口 id 必须大于 0")
    private Long interfaceInfoId;

    /**
     * 序列化版本号
     */
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

}
