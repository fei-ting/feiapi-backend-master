package com.feiting.feiapi.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 首页调用统计聚合视图。
 */
@Data
public class HomeInvokeAggregateVO implements Serializable {

    /**
     * 调用总次数。
     */
    private Long totalInvocations;

    /**
     * 成功调用次数。
     */
    private Long successInvocations;

    /**
     * 平均响应耗时，单位毫秒。
     */
    private BigDecimal averageResponseTimeMs;

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;
}
