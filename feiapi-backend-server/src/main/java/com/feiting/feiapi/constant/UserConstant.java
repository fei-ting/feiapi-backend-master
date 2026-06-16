package com.feiting.feiapi.constant;

/**
 * 用户常量
 *
 * @author yupi
 */
public final class UserConstant {

    private UserConstant() {
        // 防止实例化
    }

    /**
     * 用户登录态键
     */
    public static final String USER_LOGIN_STATE = "userLoginState";

    /**
     * 系统用户 id（虚拟用户）
     */
    public static final long SYSTEM_USER_ID = 0;

    //  region 权限

    /**
     * 默认权限
     */
    public static final String DEFAULT_ROLE = "user";

    /**
     * 管理员权限
     */
    public static final String ADMIN_ROLE = "admin";

    // endregion
}
