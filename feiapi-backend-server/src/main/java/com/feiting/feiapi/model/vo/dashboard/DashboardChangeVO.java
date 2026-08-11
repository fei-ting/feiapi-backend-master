package com.feiting.feiapi.model.vo.dashboard;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作台最近变更接口视图。
 */
@Data
public class DashboardChangeVO implements Serializable {

    /** 接口 ID。 */
    private Long id;

    /** 接口名称快照。 */
    private String name;

    /** 变更类型。 */
    private String changeType;

    /** 变更时间，ISO-8601 格式。 */
    private String time;

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;
}
