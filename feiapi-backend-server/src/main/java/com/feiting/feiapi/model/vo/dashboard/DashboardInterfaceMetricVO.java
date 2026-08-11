package com.feiting.feiapi.model.vo.dashboard;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 接口告警聚合查询结果，仅供服务层转换使用。
 */
@Data
public class DashboardInterfaceMetricVO {

    /** 接口 ID。 */
    private Long interfaceInfoId;

    /** 接口名称。 */
    private String interfaceName;

    /** 接口当前状态。 */
    private Integer status;

    /** 最近 24 小时调用总数。 */
    private Long totalInvocations;

    /** 最近 24 小时失败调用数。 */
    private Long failedInvocations;

    /** 最近 24 小时平均响应时间。 */
    private BigDecimal averageResponseTimeMs;

    /** 最近一小时调用数。 */
    private Long currentHourInvocations;

    /** 前一小时调用数。 */
    private Long previousHourInvocations;

    /** 最近一次调用时间。 */
    private Date lastInvokeTime;
}
