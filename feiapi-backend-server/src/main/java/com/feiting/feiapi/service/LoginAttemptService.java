package com.feiting.feiapi.service;

/**
 * 登录失败次数限制服务
 *
 * <p>用于对同一账号的连续登录失败进行限制，避免暴力破解。</p>
 */
public interface LoginAttemptService {

    /**
     * 判断当前账号是否允许继续登录
     *
     * @param userAccount 用户账号
     * @return 是否允许登录
     */
    boolean isLoginAllowed(String userAccount);

    /**
     * 记录一次登录失败
     *
     * @param userAccount 用户账号
     */
    void recordLoginFailure(String userAccount);

    /**
     * 记录一次登录成功并清理失败记录
     *
     * @param userAccount 用户账号
     */
    void recordLoginSuccess(String userAccount);
}
