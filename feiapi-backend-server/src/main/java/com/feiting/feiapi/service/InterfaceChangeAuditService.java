package com.feiting.feiapi.service;

import com.feiting.feiapi.model.enums.InterfaceChangeTypeEnum;

/**
 * 接口变更审计服务。
 */
public interface InterfaceChangeAuditService {

    /**
     * 记录接口变更事件。
     *
     * @param interfaceInfoId 接口 ID
     * @param interfaceName 接口名称
     * @param changeType 变更类型
     */
    void recordChange(Long interfaceInfoId, String interfaceName, InterfaceChangeTypeEnum changeType);
}
