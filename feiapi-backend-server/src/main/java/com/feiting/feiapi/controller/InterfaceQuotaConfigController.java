package com.feiting.feiapi.controller;

import com.feiting.feiapi.annotation.AuthCheck;
import com.feiting.feiapi.common.BaseResponse;
import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.common.ResultUtils;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.model.dto.interfacequotaconfig.InterfaceQuotaConfigUpdateRequest;
import com.feiting.feiapi.model.enums.UserRoleEnum;
import com.feiting.feiapi.model.vo.InterfaceQuotaConfigVO;
import com.feiting.feiapi.service.InterfaceQuotaConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

/**
 * 接口配额类型配置管理。
 */
@RestController
@RequestMapping("/interfaceQuotaConfig")
public class InterfaceQuotaConfigController {

    @Resource
    private InterfaceQuotaConfigService interfaceQuotaConfigService;

    /**
     * 查询全部配额类型配置。
     *
     * @return 配额类型配置列表
     */
    @GetMapping("/list")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<List<InterfaceQuotaConfigVO>> listInterfaceQuotaConfig() {
        return ResultUtils.success(interfaceQuotaConfigService.listConfigVO());
    }

    /**
     * 更新有限额度类型的初始额度。
     *
     * @param updateRequest 配额配置更新请求
     * @return 是否更新成功
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> updateInterfaceQuotaConfig(@Valid @RequestBody InterfaceQuotaConfigUpdateRequest updateRequest) {
        if (updateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = interfaceQuotaConfigService.updateInitialQuota(updateRequest.getQuotaType(), updateRequest.getInitialQuota());
        return ResultUtils.success(result);
    }
}
