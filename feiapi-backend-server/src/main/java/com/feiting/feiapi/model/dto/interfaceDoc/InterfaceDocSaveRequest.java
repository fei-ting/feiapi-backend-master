package com.feiting.feiapi.model.dto.interfaceDoc;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 接口文档聚合保存请求。
 */
@Data
public class InterfaceDocSaveRequest implements Serializable {

    /** 接口信息 ID。 */
    @NotNull(message = "接口 ID 不能为空")
    @Positive(message = "接口 ID 必须大于 0")
    private Long interfaceInfoId;

    /** 文档版本号。 */
    @NotBlank(message = "文档版本号不能为空")
    @Size(max = 64, message = "文档版本号长度不能超过 64")
    private String docVersion;

    /** 请求内容类型。 */
    @NotBlank(message = "请求内容类型不能为空")
    @Size(max = 128, message = "请求内容类型长度不能超过 128")
    private String requestContentType;

    /** 响应内容类型。 */
    @NotBlank(message = "响应内容类型不能为空")
    @Size(max = 128, message = "响应内容类型长度不能超过 128")
    private String responseContentType;

    /** 鉴权说明。 */
    @Size(max = 512, message = "鉴权说明长度不能超过 512")
    private String authDescription;

    /** 成功响应 JSON 示例。 */
    @Size(max = 65535, message = "成功响应示例长度不能超过 65535")
    private String successExample;

    /** 失败响应 JSON 示例。 */
    @Size(max = 65535, message = "失败响应示例长度不能超过 65535")
    private String failExample;

    /** 文档备注。 */
    @Size(max = 512, message = "文档备注长度不能超过 512")
    private String remark;

    /**
     * 全量文档参数。
     * 默认初始化为空列表，前端可直接 add 而无需 null 检查。
     * Service 层使用 isEmpty() 判断是否有数据，兼容空列表和 null。
     */
    @Valid
    @Size(max = 200, message = "文档参数数量不能超过 200")
    private List<InterfaceDocParamSaveRequest> params = new ArrayList<>();

    /**
     * 全量错误码。
     * 默认初始化为空列表，前端可直接 add 而无需 null 检查。
     * Service 层使用 isEmpty() 判断是否有数据，兼容空列表和 null。
     */
    @Valid
    @Size(max = 100, message = "错误码数量不能超过 100")
    private List<InterfaceDocErrorCodeSaveRequest> errorCodes = new ArrayList<>();

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;
}
