package com.feiting.feiapi.interfaceplatform.documentation.service.api;

import com.feiting.feiapicommon.model.entity.InterfaceInfo;

/**
 * 接口文档同步服务。
 */
public interface InterfaceDocSyncService {

    /**
     * 根据接口运行时参数模板同步结构化请求参数文档。
     *
     * @param interfaceInfo 接口信息
     */
    void syncRequestDocFromInterfaceInfo(InterfaceInfo interfaceInfo);
}
