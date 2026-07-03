package com.feiting.feiapi.service.impl.inner;

import com.feiting.feiapi.service.InterfaceInvokeLogService;
import com.feiting.feiapicommon.service.InnerInterfaceInvokeLogService;
import org.apache.dubbo.config.annotation.DubboService;

import jakarta.annotation.Resource;

/**
 * 接口调用日志内部服务实现。
 */
@DubboService
public class InnerInterfaceInvokeLogServiceImpl implements InnerInterfaceInvokeLogService {

    @Resource
    private InterfaceInvokeLogService interfaceInvokeLogService;

    @Override
    public boolean recordInvoke(long userId,
                                long interfaceInfoId,
                                String path,
                                String method,
                                Integer statusCode,
                                boolean success,
                                long responseTimeMs) {
        return interfaceInvokeLogService.recordInvoke(userId, interfaceInfoId, path, method, statusCode, success, responseTimeMs);
    }
}
