package com.feiting.feiapi.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.feiting.feiapi.model.enums.UserRoleEnum;
import com.feiting.feiapicommon.model.entity.User;

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
     * @return 登录成功的用户信息
     */
    User userLogin(String userAccount, String userPassword);

    /**
     * 获取当前登录用户
     *
     * @param sessionUser 会话中保存的用户快照
     * @return 当前登录用户
     */
    User getLoginUser(User sessionUser);

    /**
     * 是否为管理员
     *
     * @param user 用户信息
     * @return 是否为管理员
     */
    boolean isAdmin(User user);

    /**
     * 用户注销
     *
     * @param sessionUser 会话中保存的用户快照
     * @return 是否注销成功
     */
    boolean userLogout(User sessionUser);

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
    boolean updateUserRole(Long userId, UserRoleEnum newRole, Long operatorId);

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
