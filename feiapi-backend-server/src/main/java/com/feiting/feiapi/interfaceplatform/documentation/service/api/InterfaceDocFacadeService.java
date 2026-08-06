package com.feiting.feiapi.interfaceplatform.documentation.service.api;

import com.feiting.feiapi.interfaceplatform.documentation.model.dto.interfaceDoc.InterfaceDocSaveRequest;
import com.feiting.feiapi.interfaceplatform.documentation.model.vo.InterfaceDocDetailVO;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;

import java.util.List;
import java.util.Map;

/**
 * 接口文档兼容门面服务。
 *
 * <p>该接口承接现有接口文档业务入口，供文档域内部服务委托使用。</p>
 */
public interface InterfaceDocFacadeService {

    /**
     * 获取接口文档聚合详情。
     *
     * @param interfaceInfoId 接口信息 ID
     * @param admin           当前用户是否为管理员
     * @return 接口文档聚合详情
     */
    InterfaceDocDetailVO getDocDetail(Long interfaceInfoId, boolean admin);

    /**
     * 根据接口定义同步结构化请求参数文档。
     *
     * @param interfaceInfo 接口信息
     */
    void syncRequestDocFromInterfaceInfo(InterfaceInfo interfaceInfo);

    /**
     * 聚合保存接口文档。
     *
     * @param saveRequest 保存请求
     * @return 是否保存成功
     */
    boolean saveDoc(InterfaceDocSaveRequest saveRequest);

    /**
     * 批量查询接口文档状态。
     *
     * @param interfaceInfoIds 接口信息 ID 列表
     * @return 接口信息 ID 与文档状态映射
     */
    Map<Long, String> listDocStatusByInterfaceInfoIds(List<Long> interfaceInfoIds);

    /**
     * 将已有接口文档降级为草稿。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    void downgradeToDraft(Long interfaceInfoId);

    /**
     * 校验接口文档是否满足发布条件。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    void validateReadyForPublish(Long interfaceInfoId);
}
