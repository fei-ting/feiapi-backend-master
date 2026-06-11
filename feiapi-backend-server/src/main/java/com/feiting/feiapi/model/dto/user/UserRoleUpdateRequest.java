package com.feiting.feiapi.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private Long id;

    /**
     * 用户角色: user, admin
     */
    @NotBlank(message = "用户角色不能为空")
    private String userRole;

    private static final long serialVersionUID = 1L;
}
