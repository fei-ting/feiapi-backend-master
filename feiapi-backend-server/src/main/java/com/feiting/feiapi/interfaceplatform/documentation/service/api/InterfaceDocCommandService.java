package com.feiting.feiapi.interfaceplatform.documentation.service.api;

import com.feiting.feiapi.interfaceplatform.documentation.model.dto.interfaceDoc.InterfaceDocSaveRequest;

/**
 * 接口文档命令服务。
 */
public interface InterfaceDocCommandService {

    /**
     * 聚合保存接口文档。
     *
     * @param saveRequest 保存请求
     * @return 是否保存成功
     */
    boolean saveDoc(InterfaceDocSaveRequest saveRequest);
}
