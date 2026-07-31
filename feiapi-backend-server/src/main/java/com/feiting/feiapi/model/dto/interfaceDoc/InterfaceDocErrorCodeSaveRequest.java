package com.feiting.feiapi.model.dto.interfaceDoc;

import com.feiting.feiapi.validation.UnicodeLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 接口文档错误码保存请求。
 */
@Data
public class InterfaceDocErrorCodeSaveRequest implements Serializable {

    /** 错误码。 */
    @NotBlank(message = "错误码不能为空")
    @UnicodeLength(max = 64, message = "错误码长度不能超过 64 个字符")
    private String errorCode;

    /** 错误信息。 */
    @NotBlank(message = "错误信息不能为空")
    @UnicodeLength(max = 256, message = "错误信息长度不能超过 256 个字符")
    private String errorMessage;

    /** 错误说明。 */
    @UnicodeLength(max = 512, message = "错误说明长度不能超过 512 个字符")
    private String description;

    /** 解决建议。 */
    @UnicodeLength(max = 512, message = "解决建议长度不能超过 512 个字符")
    private String solution;

    /** 排序值。 */
    @NotNull(message = "错误码排序值不能为空")
    private Integer sortOrder;

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;
}
