package com.feiting.feiapi.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.service.InterfaceDocService;
import com.feiting.feiapi.service.InterfaceInfoLifecycleService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
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
        boolean result = interfaceInfoService.updateById(interfaceInfo);
        if (!result) {
            return false;
        }
        InterfaceInfo latestInterfaceInfo = interfaceInfoService.getById(interfaceInfo.getId());
        if (latestInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        interfaceDocService.syncRequestDocFromInterfaceInfo(latestInterfaceInfo);
        return true;
    }
}
