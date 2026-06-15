package com.feiting.feiapi.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录用户快照对象
 * <p>
 * 用于缓存登录用户信息，只保留鉴权和展示必要字段，
 * 避免将 userPassword、accessKey、secretKey 等敏感信息写入缓存
 * </p>
 *
 * @author feiting
 */
@Data
public class LoginUserSnapshot implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 用户 id
     */
    private Long id;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户性别
     */
    private Integer gender;

    /**
     * 用户角色
     */
    private String userRole;

    /**
     * 从 User 实体创建快照对象
     *
     * @param user 用户实体
     * @return 登录用户快照对象
     */
    public static LoginUserSnapshot fromUser(com.feiting.feiapicommon.model.entity.User user) {
        if (user == null) {
            return null;
        }
        LoginUserSnapshot snapshot = new LoginUserSnapshot();
        snapshot.setId(user.getId());
        snapshot.setUserAccount(user.getUserAccount());
        snapshot.setUserName(user.getUserName());
        snapshot.setUserAvatar(user.getUserAvatar());
        snapshot.setGender(user.getGender());
        snapshot.setUserRole(user.getUserRole());
        return snapshot;
    }

    /**
     * 将快照对象转换为 User 实体
     * <p>
     * 注意：转换后的 User 实体不包含 password、accessKey、secretKey 等敏感字段
     * </p>
     *
     * @return 用户实体（不含敏感字段）
     */
    public com.feiting.feiapicommon.model.entity.User toUser() {
        com.feiting.feiapicommon.model.entity.User user = new com.feiting.feiapicommon.model.entity.User();
        user.setId(this.id);
        user.setUserAccount(this.userAccount);
        user.setUserName(this.userName);
        user.setUserAvatar(this.userAvatar);
        user.setGender(this.gender);
        user.setUserRole(this.userRole);
        return user;
    }

    /**
     * 将快照对象转换为 User 实体，并从会话快照补充接口调用所需密钥
     * <p>
     * 缓存仍不保存 accessKey、secretKey，仅在当前会话已有密钥时补回，避免影响接口调用。
     * </p>
     *
     * @param sessionUser 会话中的用户快照
     * @return 用户实体
     */
    public com.feiting.feiapicommon.model.entity.User toUser(com.feiting.feiapicommon.model.entity.User sessionUser) {
        com.feiting.feiapicommon.model.entity.User user = toUser();
        if (sessionUser != null) {
            user.setAccessKey(sessionUser.getAccessKey());
            user.setSecretKey(sessionUser.getSecretKey());
        }
        return user;
    }
}
