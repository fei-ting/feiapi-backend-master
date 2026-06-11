package com.feiting.feiapi.model.dto.user;

import com.baomidou.mybatisplus.annotation.TableField;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户更新请求
 *
 * @author yupi
 */
@Data
public class UserUpdateRequest implements Serializable {
    /**
     * id
     */
    @NotNull(message = "用户 id 不能为空")
    @Positive(message = "用户 id 必须大于 0")
    private Long id;

    /**
     * 用户昵称
     */
    @Size(max = 256, message = "用户昵称长度不能超过 256")
    private String userName;

    /**
     * 账号
     */
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
    @Pattern(regexp = "^\\s*$|.{8,}$", message = "密码为空或长度至少 8")
    @Size(max = 512, message = "密码长度不能超过 512")
    private String userPassword;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
