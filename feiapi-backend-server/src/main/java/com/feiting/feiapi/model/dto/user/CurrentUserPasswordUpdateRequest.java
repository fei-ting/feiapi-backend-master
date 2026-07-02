package com.feiting.feiapi.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 当前登录用户密码更新请求
 */
@Data
public class CurrentUserPasswordUpdateRequest implements Serializable {

    /**
     * 旧密码
     */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /**
     * 新密码
     */
    @NotBlank(message = "新密码不能为空")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]{8,16}$",
            message = "新密码长度 8-16 位，只能包含大小写字母和数字，且必须同时包含字母和数字")
    private String newPassword;

    /**
     * 确认密码
     */
    @NotBlank(message = "确认密码不能为空")
    private String checkPassword;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}
