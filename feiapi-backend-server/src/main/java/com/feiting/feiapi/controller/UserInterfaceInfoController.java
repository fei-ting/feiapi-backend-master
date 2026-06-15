package com.feiting.feiapi.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feiting.feiapi.annotation.AuthCheck;
import com.feiting.feiapi.common.*;
import com.feiting.feiapi.component.UserSessionManager;
import com.feiting.feiapi.constant.CommonConstant;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.model.dto.userinterfaceinfo.UserInterfaceInfoAddRequest;
import com.feiting.feiapi.model.dto.userinterfaceinfo.UserInterfaceInfoQueryRequest;
import com.feiting.feiapi.model.dto.userinterfaceinfo.UserInterfaceInfoUpdateRequest;
import com.feiting.feiapi.model.enums.UserRoleEnum;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapi.utils.SortFieldUtils;
import com.feiting.feiapicommon.model.entity.User;
import com.feiting.feiapicommon.model.entity.UserInterfaceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;

/**
 * 用户调用接口信息
 */
@RestController
@RequestMapping("/userInterfaceInfo")
@Slf4j
public class UserInterfaceInfoController {
    private static final Set<String> ALLOWED_SORT_FIELDS = SortFieldUtils.allowedFields(
            "id", "userId", "interfaceInfoId", "totalNum", "leftNum", "status", "createTime", "updateTime"
    );

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Resource
    private UserService userService;

    @Resource
    private UserSessionManager userSessionManager;


    // region 增删改查

    /**
     * 创建
     *
     * @param userInterfaceInfoAddRequest
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Long> addUserInterfaceInfo(@RequestBody @Valid UserInterfaceInfoAddRequest userInterfaceInfoAddRequest) {
        if (userInterfaceInfoAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserInterfaceInfo userInterfaceInfo = new UserInterfaceInfo();
        BeanUtils.copyProperties(userInterfaceInfoAddRequest, userInterfaceInfo);
        // 校验
        userInterfaceInfoService.validUserInterfaceInfo(userInterfaceInfo, true);
        boolean result = userInterfaceInfoService.save(userInterfaceInfo);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        long newUserInterfaceInfoId = userInterfaceInfo.getId();
        return ResultUtils.success(newUserInterfaceInfoId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @return
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> deleteUserInterfaceInfo(@Valid @RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        UserInterfaceInfo oldUserInterfaceInfo = userInterfaceInfoService.getById(id);
        if (oldUserInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        boolean b = userInterfaceInfoService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新
     *
     * @param userInterfaceInfoUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> updateUserInterfaceInfo(@RequestBody @Valid UserInterfaceInfoUpdateRequest userInterfaceInfoUpdateRequest) {
        if (userInterfaceInfoUpdateRequest == null || userInterfaceInfoUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserInterfaceInfo userInterfaceInfo = new UserInterfaceInfo();
        BeanUtils.copyProperties(userInterfaceInfoUpdateRequest, userInterfaceInfo);
        // 参数校验
        userInterfaceInfoService.validUserInterfaceInfo(userInterfaceInfo, false);
        long id = userInterfaceInfoUpdateRequest.getId();
        // 判断是否存在
        UserInterfaceInfo oldUserInterfaceInfo = userInterfaceInfoService.getById(id);
        if (oldUserInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        boolean result = userInterfaceInfoService.updateById(userInterfaceInfo);
        return ResultUtils.success(result);
    }

    /**
     * 管理员根据 id 获取调用关系详情
     *
     * @param id
     * @return
     */
    @GetMapping("/admin/get")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<UserInterfaceInfo> getUserInterfaceInfoById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserInterfaceInfo userInterfaceInfo = userInterfaceInfoService.getById(id);
        return ResultUtils.success(userInterfaceInfo);
    }

    /**
     * 获取列表（仅管理员可使用）
     *
     * @param userInterfaceInfoQueryRequest
     * @return
     */
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    @GetMapping("/list")
    public BaseResponse<List<UserInterfaceInfo>> listUserInterfaceInfo(@Valid UserInterfaceInfoQueryRequest userInterfaceInfoQueryRequest) {
        UserInterfaceInfo userInterfaceInfoQuery = new UserInterfaceInfo();
        if (userInterfaceInfoQueryRequest != null) {
            BeanUtils.copyProperties(userInterfaceInfoQueryRequest, userInterfaceInfoQuery);
        }
        QueryWrapper<UserInterfaceInfo> queryWrapper = new QueryWrapper<>(userInterfaceInfoQuery);
        List<UserInterfaceInfo> userInterfaceInfoList = userInterfaceInfoService.list(queryWrapper);
        return ResultUtils.success(userInterfaceInfoList);
    }

    /**
     * 管理员分页获取调用关系列表
     *
     * @param userInterfaceInfoQueryRequest
     * @return
     */
    @GetMapping("/admin/list/page")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Page<UserInterfaceInfo>> listUserInterfaceInfoByPage(@Valid UserInterfaceInfoQueryRequest userInterfaceInfoQueryRequest) {
        return listUserInterfaceInfoPage(userInterfaceInfoQueryRequest, null);
    }

    /**
     * 当前登录用户根据 id 获取自己的调用关系详情
     *
     * @param id      调用关系 id
     * @param request HTTP 请求
     * @return 调用关系详情
     */
    @GetMapping("/my/get")
    public BaseResponse<UserInterfaceInfo> getMyUserInterfaceInfoById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = getCurrentLoginUser(request);
        UserInterfaceInfo userInterfaceInfo = userInterfaceInfoService.getById(id);
        if (userInterfaceInfo == null) {
            return ResultUtils.success(null);
        }
        if (!userInterfaceInfo.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return ResultUtils.success(userInterfaceInfo);
    }

    /**
     * 当前登录用户分页获取自己的调用关系列表
     *
     * @param userInterfaceInfoQueryRequest 查询条件
     * @param request                       HTTP 请求
     * @return 分页调用关系列表
     */
    @GetMapping("/my/list/page")
    public BaseResponse<Page<UserInterfaceInfo>> listMyUserInterfaceInfoByPage(@Valid UserInterfaceInfoQueryRequest userInterfaceInfoQueryRequest, HttpServletRequest request) {
        User loginUser = getCurrentLoginUser(request);
        return listUserInterfaceInfoPage(userInterfaceInfoQueryRequest, loginUser.getId());
    }

    /**
     * 分页查询调用关系列表。
     *
     * @param userInterfaceInfoQueryRequest 查询条件
     * @param forcedUserId                  强制用户 id，非空时仅查询该用户数据
     * @return 分页调用关系列表
     */
    private BaseResponse<Page<UserInterfaceInfo>> listUserInterfaceInfoPage(UserInterfaceInfoQueryRequest userInterfaceInfoQueryRequest, Long forcedUserId) {
        if (userInterfaceInfoQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        UserInterfaceInfo userInterfaceInfoQuery = new UserInterfaceInfo();
        BeanUtils.copyProperties(userInterfaceInfoQueryRequest, userInterfaceInfoQuery);
        if (forcedUserId != null) {
            userInterfaceInfoQuery.setUserId(forcedUserId);
        }
        long current = userInterfaceInfoQueryRequest.getCurrent();
        long size = userInterfaceInfoQueryRequest.getPageSize();
        String sortField = toDatabaseSortField(userInterfaceInfoQueryRequest.getSortField());
        String sortOrder = userInterfaceInfoQueryRequest.getSortOrder();
        // 限制爬虫
        if (size > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<UserInterfaceInfo> queryWrapper = new QueryWrapper<>(userInterfaceInfoQuery);
        queryWrapper.orderBy(StringUtils.isNotBlank(sortField),
                CommonConstant.SORT_ORDER_ASC.equals(sortOrder), sortField);
        Page<UserInterfaceInfo> userInterfaceInfoPage = userInterfaceInfoService.page(new Page<>(current, size), queryWrapper);
        return ResultUtils.success(userInterfaceInfoPage);
    }

    // endregion

    private String toDatabaseSortField(String sortField) {
        return SortFieldUtils.resolveSortField(sortField, ALLOWED_SORT_FIELDS);
    }

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
