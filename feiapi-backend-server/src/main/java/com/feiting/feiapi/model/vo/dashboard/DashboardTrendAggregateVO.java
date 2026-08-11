package com.feiting.feiapi.model.vo.dashboard;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 趋势聚合查询结果，仅供服务层转换使用。
 */
@Data
public class DashboardTrendAggregateVO {

    /** 自然小时桶索引。 */
    private Integer bucketIndex;

    /** 调用总数。 */
    private Long totalInvocations;

    /** 成功调用数。 */
    private Long successInvocations;

    /** 平均响应时间。 */
    private BigDecimal averageResponseTimeMs;
}
