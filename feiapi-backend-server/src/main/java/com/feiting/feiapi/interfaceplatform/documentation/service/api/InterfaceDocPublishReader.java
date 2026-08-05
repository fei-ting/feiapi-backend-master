package com.feiting.feiapi.interfaceplatform.documentation.service.api;

import com.feiting.feiapi.interfaceplatform.documentation.model.snapshot.InterfaceDocPublishSnapshot;

/**
 * 接口文档发布快照读取服务。
 *
 * <p>用于发布域只读获取文档主信息、参数和错误码快照。</p>
 */
public interface InterfaceDocPublishReader {

    /**
     * 获取接口文档发布快照。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 接口文档发布快照
     */
    InterfaceDocPublishSnapshot getPublishSnapshot(Long interfaceInfoId);
}
