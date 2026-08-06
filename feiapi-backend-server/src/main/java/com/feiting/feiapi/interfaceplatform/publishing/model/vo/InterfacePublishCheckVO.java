package com.feiting.feiapi.interfaceplatform.publishing.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 接口发布前检查结果视图对象。
 */
@Data
public class InterfacePublishCheckVO {

    /**
     * 是否通过全部静态发布检查。
     */
    private boolean passed;

    /**
     * 发布检查问题列表，合法无问题时返回空数组。
     */
    private List<InterfacePublishIssueVO> issues = new ArrayList<>();
}
