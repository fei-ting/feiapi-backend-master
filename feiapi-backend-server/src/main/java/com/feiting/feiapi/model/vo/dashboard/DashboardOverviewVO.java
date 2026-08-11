package com.feiting.feiapi.model.vo.dashboard;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员工作台概览统计视图。
 */
@Data
public class DashboardOverviewVO implements Serializable {

    /** 接口总数。 */
    private long totalInterfaces;

    /** 在线接口数。 */
    private long onlineInterfaces;

    /** 下线接口数。 */
    private long offlineInterfaces;

    /** 今日调用数。 */
    private long todayInvocations;

    /** 今日错误数。 */
    private long todayErrors;

    /** 最近 24 小时异常接口数。 */
    private long abnormalInterfaces;

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;
}
