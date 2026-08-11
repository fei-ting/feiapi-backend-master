package com.feiting.feiapi.model.vo.dashboard;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作台趋势图数据点。
 */
@Data
public class DashboardTrendPointVO implements Serializable {

    /** 数据点对应的标准时间。 */
    private String time;

    /** 趋势数值。 */
    private double value;

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;
}
