package com.feiting.feiapi.model.vo.dashboard;

import lombok.Data;

import java.io.Serializable;

/**
 * 工作台重点关注接口视图。
 */
@Data
public class DashboardAlertVO implements Serializable {

    /** 接口 ID。 */
    private Long id;

    /** 接口名称。 */
    private String name;

    /** 告警类型。 */
    private String alertType;

    /** 告警描述。 */
    private String description;

    /** 告警发生时间，ISO-8601 格式。 */
    private String time;

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;
}
