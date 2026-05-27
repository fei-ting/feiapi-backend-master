package com.feiting.feiapi.model.dto.interfaceInfo;


import lombok.Data;

import java.io.Serializable;

/**
 * 测试接口请求
 *
 * @Author feiting
 */
@Data
public class InterfaceInfoInvokeRequest implements Serializable {

    /**
     * 主键
     */
    private Long id;

    /**
     * 用户请求参数
     */
    private String userRequestParams;

    private static final long serialVersionUID = 1L;
}
