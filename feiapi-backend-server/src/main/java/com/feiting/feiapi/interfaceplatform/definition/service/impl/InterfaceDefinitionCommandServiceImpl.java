package com.feiting.feiapi.interfaceplatform.definition.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.definition.service.api.InterfaceDefinitionCommandService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import org.springframework.stereotype.Service;

/**
 * 接口定义命令服务实现。
 */
@Service
public class InterfaceDefinitionCommandServiceImpl implements InterfaceDefinitionCommandService {

    /**
     * 接口信息服务。
     */
    private final InterfaceInfoService interfaceInfoService;

    /**
     * 创建接口定义命令服务实现。
     *
     * @param interfaceInfoService 接口信息服务
     */
    public InterfaceDefinitionCommandServiceImpl(InterfaceInfoService interfaceInfoService) {
        this.interfaceInfoService = interfaceInfoService;
    }

    /**
     * 保存接口运行定义。
     *
     * @param interfaceInfo 接口信息
     * @return 新增接口 ID
     */
    @Override
    public Long save(InterfaceInfo interfaceInfo) {
        boolean result = interfaceInfoService.save(interfaceInfo);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return interfaceInfo.getId();
    }

    /**
     * 在接口处于下线状态时更新运行定义。
     *
     * @param interfaceInfo 接口信息
     */
    @Override
    public void updateOffline(InterfaceInfo interfaceInfo) {
        boolean result = interfaceInfoService.lambdaUpdate()
                .eq(InterfaceInfo::getId, interfaceInfo.getId())
                .eq(InterfaceInfo::getStatus, InterfaceInfoStatusEnum.OFFLINE.getValue())
                .update(interfaceInfo);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口状态已变化，请刷新后重试");
        }
    }
}
