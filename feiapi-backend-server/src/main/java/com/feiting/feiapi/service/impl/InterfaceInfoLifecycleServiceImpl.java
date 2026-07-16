package com.feiting.feiapi.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.service.InterfaceDocService;
import com.feiting.feiapi.service.InterfaceInfoLifecycleService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import com.feiting.feiapicommon.model.enums.InterfaceInfoStatusEnum;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 接口信息生命周期服务实现。
 */
@Service
public class InterfaceInfoLifecycleServiceImpl implements InterfaceInfoLifecycleService {

    /**
     * 接口信息服务。
     */
    private final InterfaceInfoService interfaceInfoService;

    /**
     * 接口文档服务。
     */
    private final InterfaceDocService interfaceDocService;

    public InterfaceInfoLifecycleServiceImpl(InterfaceInfoService interfaceInfoService,
                                             InterfaceDocService interfaceDocService) {
        this.interfaceInfoService = interfaceInfoService;
        this.interfaceDocService = interfaceDocService;
    }

    /**
     * 新增接口信息并同步结构化接口文档。
     *
     * @param interfaceInfo 接口信息
     * @return 新增接口 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addInterfaceInfoWithDoc(InterfaceInfo interfaceInfo) {
        boolean result = interfaceInfoService.save(interfaceInfo);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        interfaceDocService.syncRequestDocFromInterfaceInfo(interfaceInfo);
        return interfaceInfo.getId();
    }

    /**
     * 更新接口信息并同步结构化接口文档。
     *
     * @param interfaceInfo 接口信息
     * @return 是否更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateInterfaceInfoWithDoc(InterfaceInfo interfaceInfo) {
        InterfaceInfo oldInterfaceInfo = interfaceInfoService.getById(interfaceInfo.getId());
        if (oldInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        assertOffline(oldInterfaceInfo, "接口仅允许在下线状态修改");
        boolean result = interfaceInfoService.lambdaUpdate()
                .eq(InterfaceInfo::getId, interfaceInfo.getId())
                .eq(InterfaceInfo::getStatus, InterfaceInfoStatusEnum.OFFLINE.getValue())
                .update(interfaceInfo);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口状态已变化，请刷新后重试");
        }
        InterfaceInfo latestInterfaceInfo = interfaceInfoService.getById(interfaceInfo.getId());
        if (latestInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if (requestDocTemplateChanged(oldInterfaceInfo, latestInterfaceInfo)) {
            interfaceDocService.syncRequestDocFromInterfaceInfo(latestInterfaceInfo);
        }
        return true;
    }

    /**
     * 删除处于下线状态的接口信息。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteOfflineInterfaceInfo(Long interfaceInfoId) {
        InterfaceInfo interfaceInfo = interfaceInfoService.getById(interfaceInfoId);
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        assertOffline(interfaceInfo, "接口仅允许在下线状态删除");
        boolean result = interfaceInfoService.lambdaUpdate()
                .eq(InterfaceInfo::getId, interfaceInfoId)
                .eq(InterfaceInfo::getStatus, InterfaceInfoStatusEnum.OFFLINE.getValue())
                .remove();
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口状态已变化，请刷新后重试");
        }
        return true;
    }

    /**
     * 校验接口是否处于下线状态。
     *
     * @param interfaceInfo 接口信息
     * @param errorMessage  状态不匹配时的错误提示
     */
    private void assertOffline(InterfaceInfo interfaceInfo, String errorMessage) {
        if (!Objects.equals(interfaceInfo.getStatus(), InterfaceInfoStatusEnum.OFFLINE.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, errorMessage);
        }
    }

    /**
     * 判断运行时请求参数模板是否变化。
     *
     * @param oldInterfaceInfo    更新前接口信息
     * @param latestInterfaceInfo 更新后接口信息
     * @return 请求文档模板是否变化
     */
    private boolean requestDocTemplateChanged(InterfaceInfo oldInterfaceInfo, InterfaceInfo latestInterfaceInfo) {
        return !Objects.equals(oldInterfaceInfo.getRequestParams(), latestInterfaceInfo.getRequestParams())
                || !Objects.equals(oldInterfaceInfo.getMethod(), latestInterfaceInfo.getMethod());
    }
}
