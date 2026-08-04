package com.feiting.feiapi.model.publish;

import com.feiting.feiapi.model.entity.InterfaceDoc;
import com.feiting.feiapi.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.model.entity.InterfaceDocParam;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 接口发布内部上下文。
 *
 * <p>该对象只在 Service 层内部传递，不作为接口响应返回前端。</p>
 */
@Data
public class InterfacePublishContext {

    /**
     * 接口主记录快照。
     */
    private InterfaceInfo interfaceInfo;

    /**
     * 文档主记录快照。
     */
    private InterfaceDoc interfaceDoc;

    /**
     * 文档参数快照。
     */
    private List<InterfaceDocParam> docParams = new ArrayList<>();

    /**
     * 文档错误码快照。
     */
    private List<InterfaceDocErrorCode> errorCodes = new ArrayList<>();

    /**
     * SDK 反射方法。
     */
    private Method sdkMethod;

    /**
     * 发布探测请求参数。
     */
    private String probeRequestParams;
}
