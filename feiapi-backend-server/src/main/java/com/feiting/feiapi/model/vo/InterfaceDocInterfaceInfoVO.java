package com.feiting.feiapi.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 接口文档页面使用的接口基础信息视图。
 */
@Data
public class InterfaceDocInterfaceInfoVO implements Serializable {

    /** 主键。 */
    private Long id;

    /** 接口名称。 */
    private String name;

    /** 接口描述。 */
    private String description;

    /**
     * 接口展示地址，仅管理员可见。
     * @JsonInclude(NON_NULL) 确保非管理员时不返回此字段。
     * 权限校验兜底：Service 层 toInterfaceInfoVO() 方法根据 admin 参数决定是否设置值。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String url;

    /** 网关匹配路径。 */
    private String path;

    /**
     * 真实后端服务地址，仅管理员可见。
     * @JsonInclude(NON_NULL) 确保非管理员时不返回此字段。
     * 权限校验兜底：Service 层 toInterfaceInfoVO() 方法根据 admin 参数决定是否设置值。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String targetHost;

    /** 接口状态。 */
    private Integer status;

    /** 请求方法。 */
    private String method;

    /** 配额类型。 */
    private String quotaType;

    /** 配额类型说明。 */
    private String quotaTypeText;

    /** 初始额度。 */
    private Integer initialQuota;

    /** 创建时间。 */
    private Date createTime;

    /** 更新时间。 */
    private Date updateTime;

    /** 累计调用次数。 */
    private Integer totalNum;

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;
}
