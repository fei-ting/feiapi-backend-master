package com.feiting.feiapi.controller;

import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.annotation.AuthCheck;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.common.ResultUtils;
import com.feiting.feiapi.component.UserSessionManager;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.model.vo.InterfaceDocDetailVO;
import com.feiting.feiapi.model.dto.interfaceDoc.InterfaceDocSaveRequest;
import com.feiting.feiapi.model.enums.UserRoleEnum;
import com.feiting.feiapi.service.InterfaceDocService;
import com.feiting.feiapi.service.UserService;
import com.feiting.feiapicommon.model.entity.User;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

/**
 * 接口文档查询控制器。
 */
@RestController
@RequestMapping("/interfaceDoc")
@Validated
public class InterfaceDocController {

    /**
     * 接口文档服务。
     */
    @Resource
    private InterfaceDocService interfaceDocService;

    /**
     * 用户服务。
     */
    @Resource
    private UserService userService;

    /**
     * 用户会话管理组件。
     */
    @Resource
    private UserSessionManager userSessionManager;

    /**
     * 获取接口文档聚合详情。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param request         HTTP 请求
     * @return 接口文档聚合详情
     */
    @GetMapping("/get")
    public BaseResponse<InterfaceDocDetailVO> getInterfaceDoc(@NotNull(message = "接口 ID 不能为空")
                                                              @Positive(message = "接口 ID 必须大于 0")
                                                              Long interfaceInfoId,
                                                              HttpServletRequest request) {
        InterfaceDocDetailVO detailVO = interfaceDocService.getDocDetail(interfaceInfoId, isCurrentUserAdmin(request));
        return ResultUtils.success(detailVO);
    }

    /**
     * 聚合保存接口文档。
     *
     * @param saveRequest 保存请求
     * @return 是否保存成功
     */
    @PostMapping("/save")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> saveInterfaceDoc(@Valid @RequestBody InterfaceDocSaveRequest saveRequest) {
        return ResultUtils.success(interfaceDocService.saveDoc(saveRequest));
    }

    /**
     * 判断当前会话用户是否为管理员。
     *
     * @param request HTTP 请求
     * @return 是否为管理员
     */
    private boolean isCurrentUserAdmin(HttpServletRequest request) {
        User sessionUser = userSessionManager.getLoginUser(request);
        if (sessionUser == null || sessionUser.getId() == null) {
            return false;
        }
        try {
            return userService.isAdmin(userService.getLoginUser(sessionUser));
        } catch (BusinessException e) {
            if (ErrorCode.NOT_LOGIN_ERROR.getCode() == e.getCode()) {
                return false;
            }
            throw e;
        }
    }
}
