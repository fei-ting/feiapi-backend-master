package com.feiting.feiapi.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.feiting.feiapi.annotation.AuthCheck;
import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.common.IdRequest;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.common.ResultUtils;
import com.feiting.feiapi.component.UserSessionManager;
import com.feiting.feiapi.constant.UserConstant;
import com.feiting.feiapi.model.dto.user.*;
import com.feiting.feiapi.model.enums.UserRoleEnum;
import com.feiting.feiapi.model.vo.UserKeyVO;
import com.feiting.feiapi.model.vo.UserVO;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapicommon.model.entity.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户接口
 *
 * @author yupi
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private UserSessionManager userSessionManager;

    // region 登录相关

    /**
     * 用户注册
     *
     * @param userRegisterRequest
     * @return
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@Valid @RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     *
     * @param userLoginRequest
     * @param request
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<UserVO> userLogin(@Valid @RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.userLogin(userAccount, userPassword);
        userSessionManager.saveLoginUser(request, user);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return ResultUtils.success(userVO);
    }

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User sessionUser = userSessionManager.getLoginUser(request);
        boolean result = userService.userLogout(sessionUser);
        userSessionManager.removeLoginUser(request);
        return ResultUtils.success(result);
    }

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @GetMapping("/get/login")
    public BaseResponse<UserVO> getLoginUser(HttpServletRequest request) {
        User user = getCurrentLoginUser(request);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return ResultUtils.success(userVO);
    }

    /**
     * 获取当前登录用户访问密钥
     *
     * @param request HTTP 请求
     * @return 当前登录用户访问密钥
     */
    @GetMapping("/get/keys")
    public BaseResponse<UserKeyVO> getCurrentUserKeys(HttpServletRequest request) {
        User loginUser = getCurrentLoginUser(request);
        User currentUser = userService.getById(loginUser.getId());
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        UserKeyVO userKeyVO = new UserKeyVO();
        userKeyVO.setAccessKey(currentUser.getAccessKey());
        userKeyVO.setSecretKey(currentUser.getSecretKey());
        return ResultUtils.success(userKeyVO);
    }

    // endregion

    // region 增删改查

    /**
     * 创建用户
     *
     * @param userAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Long> addUser(@Valid @RequestBody UserAddRequest userAddRequest, HttpServletRequest request) {
        if (userAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);
        // 强制设置默认角色为普通用户，管理员不能通过此接口指定角色
        user.setUserRole(UserRoleEnum.USER.getCode());
        String password = userAddRequest.getUserPassword();
        if (StringUtils.isNotBlank(password)) {
            user.setUserPassword(userService.encodePassword(password));
        }
        boolean result = userService.save(user);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return ResultUtils.success(user.getId());
    }

    /**
     * 删除用户
     *
     * @param deleteRequest 删除请求
     * @param request       HTTP 请求
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> deleteUser(@Valid @RequestBody IdRequest idRequest, HttpServletRequest request) {
        if (idRequest == null || idRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 获取当前操作者 id
        User loginUser = getCurrentLoginUser(request);
        // 调用 Service 层的安全删除方法，包含最后管理员保护
        boolean b = userService.deleteUser(idRequest.getId(), loginUser.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户
     *
     * @param userUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> updateUser(@Valid @RequestBody UserUpdateRequest userUpdateRequest, HttpServletRequest request) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        String password = userUpdateRequest.getUserPassword();
        if (StringUtils.isNotBlank(password)) {
            user.setUserPassword(userService.encodePassword(password));
        } else {
            user.setUserPassword(null);
        }
        boolean result = userService.updateById(user);
        // 更新成功后清除用户缓存，避免旧数据被继续使用
        if (result && userUpdateRequest.getId() != null) {
            userService.evictUserCache(userUpdateRequest.getId());
        }
        return ResultUtils.success(result);
    }

    /**
     * 更新用户角色（专用接口，仅管理员可调用）
     *
     * @param userRoleUpdateRequest 角色变更请求
     * @param request               HTTP 请求
     * @return 是否更新成功
     */
    @PostMapping("/update/role")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> updateUserRole(@Valid @RequestBody UserRoleUpdateRequest userRoleUpdateRequest,
                                                HttpServletRequest request) {
        if (userRoleUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long userId = userRoleUpdateRequest.getId();
        UserRoleEnum newRole = userRoleUpdateRequest.getUserRole();
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (newRole == null || newRole == UserRoleEnum.NONE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 获取当前操作者 id 用于审计日志
        User loginUser = getCurrentLoginUser(request);
        boolean result = userService.updateUserRole(userId, newRole, loginUser.getId());
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取用户
     *
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<UserVO> getUserById(Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = getCurrentLoginUser(request);
        if (!loginUser.getId().equals(id) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return ResultUtils.success(userVO);
    }

    /**
     * 分页获取用户列表
     *
     * @param userQueryRequest
     * @param request
     * @return
     */
    @GetMapping("/list/page")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Page<UserVO>> listUserByPage(@Valid UserQueryRequest userQueryRequest, HttpServletRequest request) {
        long current = 1;
        long size = 10;
        if (userQueryRequest != null) {
            current = userQueryRequest.getCurrent();
            size = userQueryRequest.getPageSize();
        }
        if (size > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (userQueryRequest != null) {
            queryWrapper.eq(userQueryRequest.getId() != null, "id", userQueryRequest.getId());
            queryWrapper.like(StringUtils.isNotBlank(userQueryRequest.getUserName()), "user_name", userQueryRequest.getUserName());
            queryWrapper.like(StringUtils.isNotBlank(userQueryRequest.getUserAccount()), "user_account", userQueryRequest.getUserAccount());
            queryWrapper.eq(userQueryRequest.getGender() != null, "gender", userQueryRequest.getGender());
            queryWrapper.eq(StringUtils.isNotBlank(userQueryRequest.getUserRole()), "user_role", userQueryRequest.getUserRole());
        }
        Page<User> userPage = userService.page(new Page<>(current, size), queryWrapper);
        Page<UserVO> userVOPage = new PageDTO<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        List<UserVO> userVOList = userPage.getRecords().stream().map(user -> {
            UserVO userVO = new UserVO();
            BeanUtils.copyProperties(user, userVO);
            return userVO;
        }).collect(Collectors.toList());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }

    // endregion

    /**
     * 从当前 HTTP 会话中获取登录用户
     *
     * @param request HTTP 请求
     * @return 当前登录用户
     */
    private User getCurrentLoginUser(HttpServletRequest request) {
        return userService.getLoginUser(userSessionManager.getLoginUser(request));
    }
}
