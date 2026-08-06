package com.feiting.feiapi.interfaceplatform.documentation.model.snapshot;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/**
 * 接口文档发布快照。
 *
 * <p>该模型用于发布检查读取文档域数据，不允许调用方通过快照回写文档表。</p>
 */
@Value
@Builder
public class InterfaceDocPublishSnapshot {

    /**
     * 接口信息 ID。
     */
    Long interfaceInfoId;

    /**
     * 文档主记录 ID。
     */
    Long docId;

    /**
     * 文档维护状态。
     */
    String docStatus;

    /**
     * 文档版本号。
     */
    String docVersion;

    /**
     * 请求内容类型。
     */
    String requestContentType;

    /**
     * 响应内容类型。
     */
    String responseContentType;

    /**
     * 成功响应 JSON 示例。
     */
    String successExample;

    /**
     * 失败响应 JSON 示例。
     */
    String failExample;

    /**
     * 文档备注。
     */
    String remark;

    /**
     * 文档参数快照列表。
     */
    @Singular("docParam")
    List<InterfaceDocParamSnapshot> docParams;

    /**
     * 文档错误码快照列表。
     */
    @Singular("errorCode")
    List<InterfaceDocErrorCodeSnapshot> errorCodes;
}
