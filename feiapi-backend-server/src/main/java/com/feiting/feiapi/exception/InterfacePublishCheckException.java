package com.feiting.feiapi.exception;

import com.feiting.feiapi.interfaceplatform.publishing.model.vo.InterfacePublishIssueVO;

import java.util.List;

/**
 * 接口发布前静态检查失败异常兼容类型。
 *
 * <p>实际异常已迁移至发布治理域，本类型保留给历史测试和调用方直接引用。</p>
 */
public class InterfacePublishCheckException
        extends com.feiting.feiapi.interfaceplatform.publishing.exception.InterfacePublishCheckException {

    /**
     * 创建发布前检查失败异常。
     *
     * @param issues 检查问题列表
     */
    public InterfacePublishCheckException(List<InterfacePublishIssueVO> issues) {
        super(issues);
    }
}
