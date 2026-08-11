package com.feiting.feiapi.model.vo.dashboard;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理员工作台运行趋势视图。
 */
@Data
public class DashboardTrendsVO implements Serializable {

    /** 成功率趋势。 */
    private List<DashboardTrendPointVO> successRate = new ArrayList<>();

    /** 调用量趋势。 */
    private List<DashboardTrendPointVO> invocationCount = new ArrayList<>();

    /** 错误率趋势。 */
    private List<DashboardTrendPointVO> errorRate = new ArrayList<>();

    /** 响应时间趋势。 */
    private List<DashboardTrendPointVO> responseTime = new ArrayList<>();

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;
}
