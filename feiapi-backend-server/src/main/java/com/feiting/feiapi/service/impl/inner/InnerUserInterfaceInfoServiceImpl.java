package com.feiting.feiapi.service.impl.inner;

import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapicommon.service.InnerUserInterfaceInfoService;
import org.apache.dubbo.config.annotation.DubboService;

import jakarta.annotation.Resource;

/**
 * @Author feiting
 */
@DubboService
public class InnerUserInterfaceInfoServiceImpl implements InnerUserInterfaceInfoService {

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;

    @Override
    public boolean invokeCount(long userId, long interfaceInfoId) {
        return userInterfaceInfoService.invokeCount(userId, interfaceInfoId);
    }

    @Override
    public boolean rollbackInvokeCount(long userId, long interfaceInfoId) {
        return userInterfaceInfoService.rollbackInvokeCount(userId, interfaceInfoId);
    }

    @Override
    public boolean leftNumIsEnough(long userId, long interfaceInfoId) {
        return userInterfaceInfoService.leftNumIsEnough(userId, interfaceInfoId);
    }
}
