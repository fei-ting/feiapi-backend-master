package com.feiting.feiapi.model.dto.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户个人资料更新请求
 */
@Data
public class CurrentUserProfileUpdateRequest implements Serializable {

    /**
     * 用户昵称
     */
    @NotBlank(message = "用户昵称不能为空")
    @Size(max = 256, message = "用户昵称长度不能超过 256")
    private String userName;

    /**
     * 性别，0 表示男，1 表示女
     */
    @NotNull(message = "性别不能为空")
    @Min(value = 0, message = "性别不能小于 0")
    @Max(value = 1, message = "性别不能大于 1")
    private Integer gender;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}
