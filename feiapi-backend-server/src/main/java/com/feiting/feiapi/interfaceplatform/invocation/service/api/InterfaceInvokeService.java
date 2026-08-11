package com.feiting.feiapi.interfaceplatform.invocation.service.api;

import com.feiting.feiapi.interfaceplatform.invocation.model.vo.InterfaceInvokeResultVO;
import com.feiting.feiapicommon.model.entity.User;

/**
 * 在线调试调用服务。
 */
public interface InterfaceInvokeService {

    /**
     * 使用当前登录用户的 APIKey 通过 SDK 发起真实接口调用。
     *
     * @param interfaceInfoId  接口信息 ID
     * @param userRequestParams 用户请求参数 JSON
     * @param loginUser        当前登录用户
     * @return 在线调试调用结果
     */
    InterfaceInvokeResultVO invoke(long interfaceInfoId, String userRequestParams, User loginUser);
}
