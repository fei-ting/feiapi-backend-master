package com.feiting.feiapicommon.service;

import com.feiting.feiapicommon.model.entity.InterfaceInfo;

/**
 *
 */
public interface InnerInterfaceInfoService {

    /**
     * 从数据库中查询模拟接口是否存在（请求路径、请求方法、请求参数）
     */
    InterfaceInfo getInterfaceInfo(String path, String method);

    /**
     * 查询发布验证中的接口信息，仅供发布探测链路使用。
     */
    InterfaceInfo getPublishingInterfaceInfo(String path, String method);
}
