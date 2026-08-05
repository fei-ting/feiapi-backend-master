package com.feiting.feiapi.interfaceplatform.publishing.exception;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.publishing.model.vo.InterfacePublishIssueVO;

import java.util.List;

/**
 * 接口发布前静态检查失败异常。
 */
public class InterfacePublishCheckException extends BusinessException {

    /**
     * 发布检查问题列表。
     */
    private final List<InterfacePublishIssueVO> issues;

    /**
     * 创建发布前检查失败异常。
     *
     * @param issues 检查问题列表
     */
    public InterfacePublishCheckException(List<InterfacePublishIssueVO> issues) {
        super(ErrorCode.PUBLISH_CHECK_FAILED, "接口发布前检查未通过，请先修复检查问题");
        this.issues = issues;
    }

    /**
     * 获取发布检查问题列表。
     *
     * @return 检查问题列表
     */
    public List<InterfacePublishIssueVO> getIssues() {
        return issues;
    }
}
