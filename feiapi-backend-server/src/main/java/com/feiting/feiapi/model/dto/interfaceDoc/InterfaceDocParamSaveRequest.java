package com.feiting.feiapi.model.dto.interfaceDoc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 接口文档参数保存请求。
 */
@Data
public class InterfaceDocParamSaveRequest implements Serializable {

    /** 本次保存请求内唯一的参数键。 */
    @NotBlank(message = "参数键不能为空")
    @Size(max = 64, message = "参数键长度不能超过 64")
    private String paramKey;

    /** 父级参数键，仅响应参数允许填写。 */
    @Size(max = 64, message = "父级参数键长度不能超过 64")
    private String parentParamKey;

    /** 参数场景，取值为 HEADER、QUERY、BODY 或 RESPONSE。 */
    @NotBlank(message = "参数场景不能为空")
    @Size(max = 32, message = "参数场景长度不能超过 32")
    @Pattern(regexp = "HEADER|QUERY|BODY|RESPONSE", message = "参数场景必须是 HEADER、QUERY、BODY 或 RESPONSE")
    private String paramScene;

    /** 参数名称。 */
    @NotBlank(message = "参数名称不能为空")
    @Size(max = 128, message = "参数名称长度不能超过 128")
    private String name;

    /** 参数类型。 */
    @NotBlank(message = "参数类型不能为空")
    @Size(max = 64, message = "参数类型长度不能超过 64")
    private String type;

    /** 字段是否必须出现。 */
    @NotNull(message = "必填标识不能为空")
    private Boolean required;

    /** 字段值是否允许为空，响应参数必须明确填写。 */
    private Boolean nullable;

    /** 默认值。 */
    @Size(max = 512, message = "默认值长度不能超过 512")
    private String defaultValue;

    /** 示例值。 */
    @Size(max = 1024, message = "示例值长度不能超过 1024")
    private String exampleValue;

    /** 参数说明。 */
    @Size(max = 512, message = "参数说明长度不能超过 512")
    private String description;

    /** 校验规则展示说明。 */
    @Size(max = 512, message = "校验规则长度不能超过 512")
    private String validationRule;

    /** 排序值。 */
    @NotNull(message = "排序值不能为空")
    private Integer sortOrder;

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;
}
