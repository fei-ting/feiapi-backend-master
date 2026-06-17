package com.feiting.feiapicommon.service;

import com.feiting.feiapicommon.model.entity.InterfaceInfo;

/**
 * 内部接口信息服务
 */
public interface InnerInterfaceInfoService {

    /**
     * 根据接口路径和请求方法查询已上线接口
     *
     * @param path   接口路径
     * @param method 请求方法
     * @return 已上线接口信息
     */
    InterfaceInfo getInterfaceInfo(String path, String method);

    /**
     * 查询发布验证中的接口信息，仅供发布探测链路使用。
     *
     * @param path   接口路径
     * @param method 请求方法
     * @return 发布验证中的接口信息
     */
    InterfaceInfo getPublishingInterfaceInfo(String path, String method);
}
