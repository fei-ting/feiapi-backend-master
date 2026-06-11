package com.feiting.feiapi.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.feiting.feiapicommon.model.entity.User;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户服务
 *
 * @author yupi
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    User userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    boolean isAdmin(HttpServletRequest request);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 对明文密码进行BCrypt加密
     *
     * @param rawPassword 明文密码
     * @return 加密后的密码
     */
    String encodePassword(String rawPassword);

    /**
     * 更新用户角色
     *
     * @param userId     目标用户 id
     * @param newRole    新角色
     * @param operatorId 操作者 id
     * @return 是否更新成功
     */
    boolean updateUserRole(Long userId, String newRole, Long operatorId);

    /**
     * 判断指定用户是否是最后一个管理员
     *
     * @param userId 用户 id
     * @return 是否是最后一个管理员
     */
    boolean isLastAdmin(Long userId);

    /**
     * 安全删除用户（含最后管理员保护）
     *
     * @param userId     要删除的用户 id
     * @param operatorId 操作者 id
     * @return 是否删除成功
     */
    boolean deleteUser(Long userId, Long operatorId);
}
