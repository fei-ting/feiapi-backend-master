package com.feiting.feiapi.model.dto.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户创建请求
 *
 * @author yupi
 */
@Data
public class UserAddRequest implements Serializable {

    /**
     * 用户昵称
     */
    @Size(max = 256, message = "用户昵称长度不能超过 256")
    private String userName;

    /**
     * 账号
     */
    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 256, message = "账号长度必须在 4 到 256 之间")
    private String userAccount;

    /**
     * 用户头像
     */
    @Size(max = 1024, message = "用户头像长度不能超过 1024")
    private String userAvatar;

    /**
     * 性别
     */
    @Min(value = 0, message = "性别不能小于 0")
    @Max(value = 1, message = "性别不能大于 1")
    private Integer gender;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 512, message = "密码长度必须在 8 到 512 之间")
    private String userPassword;

    private static final long serialVersionUID = 1L;
}
