package com.feiting.feiapi.model.dto.interfaceDoc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 接口文档错误码保存请求。
 */
@Data
public class InterfaceDocErrorCodeSaveRequest implements Serializable {

    /** 错误码。 */
    @NotBlank(message = "错误码不能为空")
    @Size(max = 64, message = "错误码长度不能超过 64")
    private String errorCode;

    /** 错误信息。 */
    @NotBlank(message = "错误信息不能为空")
    @Size(max = 256, message = "错误信息长度不能超过 256")
    private String errorMessage;

    /** 错误说明。 */
    @Size(max = 512, message = "错误说明长度不能超过 512")
    private String description;

    /** 解决建议。 */
    @Size(max = 512, message = "解决建议长度不能超过 512")
    private String solution;

    /** 排序值。 */
    @NotNull(message = "错误码排序值不能为空")
    private Integer sortOrder;

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;
}
