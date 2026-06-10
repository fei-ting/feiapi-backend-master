package com.feiting.feiapicommon.service;

import com.feiting.feiapicommon.model.vo.InvokeUserVO;


/**
 * 用户服务
 *
 * @author yupi
 */
public interface InnerUserService {

    /**
     * 数据库中查是否已分配给用户密钥（accessKey）
     *
     * @param accessKey
     * @return 内部调用用户最小信息
     */
    InvokeUserVO getInvokeUser(String accessKey);
}
