package com.feiting.feiapi.interfaceplatform.publishing.service.api;

import com.feiting.feiapi.interfaceplatform.publishing.model.context.InterfacePublishContext;
import com.feiting.feiapi.interfaceplatform.publishing.model.vo.InterfacePublishCheckVO;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;

/**
 * 接口发布前静态检查服务。
 */
public interface InterfacePublishCheckService {

    /**
     * 执行管理员只读发布前检查。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 发布前检查结果
     */
    InterfacePublishCheckVO check(Long interfaceInfoId);

    /**
     * 基于接口 ID 构造发布上下文并校验静态门禁。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 发布上下文
     */
    InterfacePublishContext buildContextForPublish(Long interfaceInfoId);

    /**
     * 基于已锁定的接口快照构造发布上下文并校验静态门禁。
     *
     * @param lockedInterfaceInfo 已在事务中锁定的接口主记录
     * @return 发布上下文
     */
    InterfacePublishContext buildContextForPublish(InterfaceInfo lockedInterfaceInfo);
}
