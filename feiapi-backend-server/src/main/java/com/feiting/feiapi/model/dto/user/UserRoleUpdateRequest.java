package com.feiting.feiapi.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户角色变更请求
 *
 * @author yupi
 */
@Data
public class UserRoleUpdateRequest implements Serializable {

    /**
     * 用户 id
     */
    @NotNull(message = "用户 id 不能为空")
    @Positive(message = "用户 id 必须大于 0")
    private Long id;

    /**
     * 用户角色: user, admin
     */
    @NotBlank(message = "用户角色不能为空")
    @Pattern(regexp = "^(user|admin)$", message = "用户角色只能是 user 或 admin")
    private String userRole;

    private static final long serialVersionUID = 1L;
}
