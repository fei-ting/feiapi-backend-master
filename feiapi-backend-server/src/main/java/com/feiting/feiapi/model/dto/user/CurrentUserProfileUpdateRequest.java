package com.feiting.feiapi.model.dto.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    @Size(min = 2, max = 16, message = "昵称需为 2-16 位")
    @Pattern(regexp = "^[\\p{IsHan}A-Za-z0-9]{2,16}$", message = "昵称只能包含中文、英文和数字")
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
