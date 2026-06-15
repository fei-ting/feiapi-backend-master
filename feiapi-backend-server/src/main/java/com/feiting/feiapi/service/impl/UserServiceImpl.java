package com.feiting.feiapi.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.config.CacheConfig;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.UserMapper;
import com.feiting.feiapi.mapper.UserRoleChangeLogMapper;
import com.feiting.feiapi.model.enums.UserRoleEnum;
import com.feiting.feiapi.service.LoginAttemptService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.entity.UserRoleChangeLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import jakarta.annotation.Resource;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;


/**
 * 用户服务实现类
 *
 * @author yupi
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    /**
     * 登录失败次数过多时的提示信息
     */
    private static final String LOGIN_ATTEMPT_LOCK_MESSAGE = "登录失败次数过多，请稍后再试";

    /**
     * accessKey 随机字节长度
     */
    private static final int ACCESS_KEY_RANDOM_BYTE_LENGTH = 32;

    /**
     * secretKey 随机字节长度
     */
    private static final int SECRET_KEY_RANDOM_BYTE_LENGTH = 48;

    /**
     * 安全随机数生成器
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserRoleChangeLogMapper userRoleChangeLogMapper;

    @Resource
    private LoginAttemptService loginAttemptService;

    @Resource
    private CacheManager cacheManager;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 账户不能重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 2. 加密
        String encryptPassword = passwordEncoder.encode(userPassword);

        // 3. 分配不可预测的 accessKey 和 secretKey
        String accessKey = generateSecureKey(ACCESS_KEY_RANDOM_BYTE_LENGTH);
        String secretKey = generateSecureKey(SECRET_KEY_RANDOM_BYTE_LENGTH);

        // 4. 插入数据，并将并发注册触发的唯一键冲突转为业务异常
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setAccessKey(accessKey);
        user.setSecretKey(secretKey);
        boolean saveResult;
        try {
            saveResult = this.save(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    /**
     * 生成 URL 安全的随机密钥
     *
     * @param byteLength 随机字节长度
     * @return URL 安全的随机密钥
     */
    private String generateSecureKey(int byteLength) {
        byte[] randomBytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Override
    public User userLogin(String userAccount, String userPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        if (!loginAttemptService.isLoginAllowed(userAccount)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, LOGIN_ATTEMPT_LOCK_MESSAGE);
        }
        // 2. 查询用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_account", userAccount);
        User user = userMapper.selectOne(queryWrapper);
        // 用户不存在或密码不匹配
        if (user == null || !passwordEncoder.matches(userPassword, user.getUserPassword())) {
            log.info("user login failed, userAccount cannot match userPassword");
            loginAttemptService.recordLoginFailure(userAccount);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 3. 记录登录成功，HTTP 会话写入由 Web 层负责
        loginAttemptService.recordLoginSuccess(userAccount);
        return user;
    }

    /**
     * 获取当前登录用户
     * <p>
     * 优先从缓存读取，缓存未命中时再查询数据库
     * 缓存 key 为用户 id，缓存内容为用户实体
     * </p>
     *
     * @param sessionUser 会话中保存的用户快照
     * @return 当前登录用户
     */
    @Override
    public User getLoginUser(User sessionUser) {
        // 1. 先判断是否已登录
        if (sessionUser == null || sessionUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        long userId = sessionUser.getId();
        String cacheKey = String.valueOf(userId);

        // 2. 尝试从缓存读取
        Cache cache = cacheManager.getCache(CacheConfig.LOGIN_USER_CACHE_NAME);
        if (cache != null) {
            Cache.ValueWrapper valueWrapper = cache.get(cacheKey);
            if (valueWrapper != null) {
                User cachedUser = (User) valueWrapper.get();
                if (cachedUser != null) {
                    log.debug("从缓存获取用户信息，userId: {}", userId);
                    return cachedUser;
                }
            }
        }

        // 3. 缓存未命中，从数据库查询
        User currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 4. 将用户信息放入缓存
        if (cache != null) {
            cache.put(cacheKey, currentUser);
            log.debug("用户信息已缓存，userId: {}", userId);
        }

        return currentUser;
    }

    /**
     * 是否为管理员
     *
     * @param user 用户信息
     * @return 是否为管理员
     */
    @Override
    public boolean isAdmin(User user) {
        // 仅管理员可查询
        return user != null && UserRoleEnum.ADMIN.getCode().equals(user.getUserRole());
    }

    @Override
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * 用户注销
     *
     * @param sessionUser 会话中保存的用户快照
     */
    @Override
    public boolean userLogout(User sessionUser) {
        if (sessionUser == null || sessionUser.getId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        return true;
    }

    /**
     * 更新用户角色
     *
     * @param userId     目标用户 id
     * @param newRole    新角色
     * @param operatorId 操作者 id
     * @return 是否更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUserRole(Long userId, UserRoleEnum newRole, Long operatorId) {
        // 1. 校验角色是否合法
        if (newRole == null || newRole == UserRoleEnum.NONE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的角色值");
        }
        String newRoleCode = newRole.getCode();

        // 2. 校验操作者存在且必须是管理员
        User operator = this.getById(operatorId);
        if (operator == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "操作者不存在");
        }
        if (!UserRoleEnum.ADMIN.getCode().equals(operator.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "非管理员无权修改用户角色");
        }

        // 3. 查询目标用户
        User targetUser = this.getById(userId);
        if (targetUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        String oldRole = targetUser.getUserRole();

        // 4. 角色未变更时直接返回
        if (newRoleCode.equals(oldRole)) {
            return true;
        }

        // 5. 最后一个管理员保护：如果目标当前是 admin 且新角色不是 admin
        //    使用 FOR UPDATE 锁住管理员集合，确保并发安全
        if (UserRoleEnum.ADMIN.getCode().equals(oldRole) && newRole != UserRoleEnum.ADMIN) {
            // 使用 FOR UPDATE 锁住管理员行，防止并发修改
            List<Long> adminIds = userMapper.selectAdminIdsForUpdate();
            if (adminIds.size() <= 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "不能降级最后一个管理员");
            }
        }

        // 6. 更新角色
        targetUser.setUserRole(newRoleCode);
        boolean result = this.updateById(targetUser);

        // 7. 角色变更成功后插入审计记录并清理缓存
        if (result) {
            insertAuditLog(operatorId, userId, oldRole, newRoleCode, "管理员通过专用接口变更用户角色");
            evictUserCache(userId);
        }

        return result;
    }

    /**
     * 安全删除用户（含最后管理员保护）
     *
     * @param userId     要删除的用户 id
     * @param operatorId 操作者 id
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long userId, Long operatorId) {
        // 1. 查询目标用户
        User targetUser = this.getById(userId);
        if (targetUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        // 2. 如果删除的是管理员，需要检查是否是最后一个管理员
        if (UserRoleEnum.ADMIN.getCode().equals(targetUser.getUserRole())) {
            // 使用 FOR UPDATE 锁住管理员行，防止并发删除或降级最后一个管理员
            List<Long> adminIds = userMapper.selectAdminIdsForUpdate();
            if (adminIds.size() <= 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "不能删除最后一个管理员");
            }
        }

        // 3. 执行删除
        boolean result = this.removeById(userId);

        // 4. 删除成功后记录审计日志并清理缓存
        if (result) {
            insertAuditLog(operatorId, userId, targetUser.getUserRole(), null, "管理员删除用户");
            evictUserCache(userId);
        }

        return result;
    }

    /**
     * 插入审计日志
     *
     * @param operatorId    操作者 id
     * @param targetUserId  目标用户 id
     * @param oldRole       旧角色
     * @param newRole       新角色（删除时为 null）
     * @param remark        备注
     */
    private void insertAuditLog(Long operatorId, Long targetUserId, String oldRole, String newRole, String remark) {
        UserRoleChangeLog auditLog = new UserRoleChangeLog();
        auditLog.setOperatorId(operatorId);
        auditLog.setTargetUserId(targetUserId);
        auditLog.setOldRole(oldRole);
        auditLog.setNewRole(newRole);
        auditLog.setRemark(remark);
        userRoleChangeLogMapper.insert(auditLog);

        // 日志保留作为辅助手段
        log.info("用户角色变更审计 - 操作者: {}, 目标用户: {}, 旧角色: {}, 新角色: {}, 备注: {}, 审计ID: {}",
                operatorId, targetUserId, oldRole, newRole, remark, auditLog.getId());
    }

    /**
     * 清除指定用户的缓存
     * <p>
     * 用于用户资料变更、角色变更、用户删除等场景，确保缓存数据一致性
     * </p>
     *
     * @param userId 用户 id
     */
    @Override
    public void evictUserCache(Long userId) {
        if (userId == null) {
            return;
        }
        Cache cache = cacheManager.getCache(CacheConfig.LOGIN_USER_CACHE_NAME);
        if (cache != null) {
            cache.evict(String.valueOf(userId));
            log.debug("已清除用户缓存，userId: {}", userId);
        }
    }

    /**
     * 判断指定用户是否是最后一个管理员
     *
     * @param userId 用户 id
     * @return 是否是最后一个管理员
     */
    @Override
    public boolean isLastAdmin(Long userId) {
        // 查询该用户是否是管理员
        User user = this.getById(userId);
        if (user == null || !UserRoleEnum.ADMIN.getCode().equals(user.getUserRole())) {
            return false;
        }
        // 查询管理员总数
        QueryWrapper<User> adminQuery = new QueryWrapper<>();
        adminQuery.eq("user_role", UserRoleEnum.ADMIN.getCode());
        long adminCount = userMapper.selectCount(adminQuery);
        return adminCount <= 1;
    }

}




