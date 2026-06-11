package com.feiting.feiapi.model.dto.user;

import com.feiting.feiapi.common.PageRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户查询请求
 *
 * @author yupi
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserQueryRequest extends PageRequest implements Serializable {
    /**
     * id
     */
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
    @Size(max = 256, message = "账号长度不能超过 256")
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
     * 用户角色: user, admin
     */
    @Size(max = 256, message = "用户角色长度不能超过 256")
    private String userRole;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
