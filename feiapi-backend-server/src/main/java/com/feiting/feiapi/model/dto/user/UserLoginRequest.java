package com.feiting.feiapi.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求体
 *
 * @author yupi
 */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 账号：4-10 位，以字母开头，只能包含大小写字母和数字
     */
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9]{3,9}$",
            message = "账号长度 4-10 位，以字母开头，只能包含大小写字母和数字")
    private String userAccount;

    /**
     * 密码：8-16 位，只能包含大小写字母和数字，且必须同时包含字母和数字
     */
    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9]{8,16}$",
            message = "密码长度 8-16 位，只能包含大小写字母和数字，且必须同时包含字母和数字")
    private String userPassword;
}
