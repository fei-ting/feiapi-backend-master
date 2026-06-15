package com.feiting.feiapi.model.dto.user;

import com.feiting.feiapi.model.enums.UserRoleEnum;
import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "用户角色不能为空")
    private UserRoleEnum userRole;

    private static final long serialVersionUID = 1L;
}
