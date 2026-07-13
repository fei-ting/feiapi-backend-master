package com.feiting.feiapi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.feiting.feiapi.model.entity.InterfaceDoc;
import com.feiting.feiapi.model.vo.InterfaceDocDetailVO;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;

/**
 * 接口文档主信息服务。
 */
public interface InterfaceDocService extends IService<InterfaceDoc> {

    /**
     * 获取接口文档聚合详情。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param admin           当前用户是否为管理员
     * @return 接口文档聚合详情
     */
    InterfaceDocDetailVO getDocDetail(Long interfaceInfoId, boolean admin);

    /**
     * 根据接口运行时参数模板同步结构化请求参数文档。
     *
     * @param interfaceInfo 接口信息
     */
    void syncRequestDocFromInterfaceInfo(InterfaceInfo interfaceInfo);
}
