package com.feiting.feiapi.model.vo.dashboard;

import lombok.Data;

/**
 * 工作台调用概览聚合结果。
 */
@Data
public class DashboardInvokeAggregateVO {

    /** 调用总数。 */
    private Long totalInvocations;

    /** 错误调用数。 */
    private Long errorInvocations;
}
