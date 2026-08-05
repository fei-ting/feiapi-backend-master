package com.feiting.feiapi.interfaceplatform.documentation.service.api;

import com.feiting.feiapi.interfaceplatform.documentation.model.vo.InterfaceDocDetailVO;

import java.util.List;
import java.util.Map;

/**
 * 接口文档查询服务。
 */
public interface InterfaceDocQueryService {

    /**
     * 获取接口文档聚合详情。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param admin           当前用户是否为管理员
     * @return 接口文档聚合详情
     */
    InterfaceDocDetailVO getDocDetail(Long interfaceInfoId, boolean admin);

    /**
     * 批量查询接口文档状态。
     *
     * @param interfaceInfoIds 接口信息 ID 列表
     * @return 接口信息 ID 与文档状态映射
     */
    Map<Long, String> listDocStatusByInterfaceInfoIds(List<Long> interfaceInfoIds);
}
