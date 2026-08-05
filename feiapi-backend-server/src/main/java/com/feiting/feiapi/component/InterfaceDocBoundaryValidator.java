package com.feiting.feiapi.component;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.model.dto.interfaceDoc.InterfaceDocErrorCodeSaveRequest;
import com.feiting.feiapi.model.dto.interfaceDoc.InterfaceDocParamSaveRequest;
import com.feiting.feiapi.model.dto.interfaceDoc.InterfaceDocSaveRequest;
import com.feiting.feiapi.model.entity.InterfaceDoc;
import com.feiting.feiapi.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.model.entity.InterfaceDocParam;
import com.feiting.feiapi.model.enums.InterfaceDocParamSceneEnum;
import com.feiting.feiapi.utils.TextSizeUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 接口文档数量、Unicode 长度与 UTF-8 字节边界校验器。
 */
@Component
public class InterfaceDocBoundaryValidator {

    /** 请求参数数量上限。 */
    public static final int MAX_REQUEST_PARAM_COUNT = 100;

    /** 响应字段数量上限。 */
    public static final int MAX_RESPONSE_PARAM_COUNT = 200;

    /** 请求参数和响应字段合计数量上限。 */
    public static final int MAX_TOTAL_PARAM_COUNT = 200;

    /** 错误码数量上限。 */
    public static final int MAX_ERROR_CODE_COUNT = 100;

    /** 参数名称最大 Unicode 码点数量。 */
    public static final int MAX_PARAM_NAME_LENGTH = 128;

    /** 参数默认值最大 Unicode 码点数量。 */
    public static final int MAX_PARAM_DEFAULT_VALUE_LENGTH = 512;

    /** 参数示例值最大 Unicode 码点数量。 */
    public static final int MAX_PARAM_EXAMPLE_VALUE_LENGTH = 1024;

    /** 参数说明、校验规则和公开备注最大 Unicode 码点数量。 */
    public static final int MAX_DESCRIPTION_LENGTH = 512;

    /** 错误码最大 Unicode 码点数量。 */
    public static final int MAX_ERROR_CODE_LENGTH = 64;

    /** 错误信息最大 Unicode 码点数量。 */
    public static final int MAX_ERROR_MESSAGE_LENGTH = 256;

    /** 单个 JSON 示例最大 UTF-8 字节数。 */
    public static final int MAX_JSON_EXAMPLE_BYTES = 65535;

    /**
     * 校验接口文档聚合保存请求的全部 2.8 边界。
     *
     * @param saveRequest 聚合保存请求
     */
    public void validateSaveRequest(InterfaceDocSaveRequest saveRequest) {
        if (saveRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        List<InterfaceDocParamSaveRequest> params = saveRequest.getParams();
        List<InterfaceDocErrorCodeSaveRequest> errorCodes = saveRequest.getErrorCodes();
        validateCounts(countRequestParams(params), countResponseParams(params), sizeOf(errorCodes));
        assertUnicodeLength(saveRequest.getRemark(), MAX_DESCRIPTION_LENGTH, "文档备注长度不能超过 512 个字符");
        assertUtf8ByteLength(saveRequest.getSuccessExample(), MAX_JSON_EXAMPLE_BYTES,
                "成功响应示例不能超过 65535 个 UTF-8 字节");
        assertUtf8ByteLength(saveRequest.getFailExample(), MAX_JSON_EXAMPLE_BYTES,
                "失败响应示例不能超过 65535 个 UTF-8 字节");
        if (params != null) {
            params.stream().filter(Objects::nonNull).forEach(this::validateParamRequest);
        }
        if (errorCodes != null) {
            errorCodes.stream().filter(Objects::nonNull).forEach(this::validateErrorCodeRequest);
        }
    }

    /**
     * 校验已持久化接口文档的全部 2.8 边界。
     *
     * @param doc        文档主记录
     * @param params     全部文档参数
     * @param errorCodes 全部接口错误码
     */
    public void validatePersistedDoc(InterfaceDoc doc, List<InterfaceDocParam> params,
                                     List<InterfaceDocErrorCode> errorCodes) {
        if (doc == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口文档待完善，请先完成文档维护");
        }
        validateCounts(countPersistedRequestParams(params), countPersistedResponseParams(params), sizeOf(errorCodes));
        assertUnicodeLength(doc.getRemark(), MAX_DESCRIPTION_LENGTH, "文档备注长度不能超过 512 个字符");
        assertUtf8ByteLength(doc.getSuccessExample(), MAX_JSON_EXAMPLE_BYTES,
                "成功响应示例不能超过 65535 个 UTF-8 字节");
        assertUtf8ByteLength(doc.getFailExample(), MAX_JSON_EXAMPLE_BYTES,
                "失败响应示例不能超过 65535 个 UTF-8 字节");
        if (params != null) {
            params.stream().filter(Objects::nonNull).forEach(this::validatePersistedParam);
        }
        if (errorCodes != null) {
            errorCodes.stream().filter(Objects::nonNull).forEach(this::validatePersistedErrorCode);
        }
    }

    /**
     * 校验请求参数、响应字段及合计数量。
     * <p>
     * 此方法仅用于运行时参数同步场景，
     * 该场景只涉及请求参数，不涉及错误码，因此 {@code errorCodeCount} 固定传 0。
     * 聚合保存和发布前校验应使用 {@link #validateSaveRequest} 或 {@link #validatePersistedDoc}，
     * 它们会同时校验错误码数量。
     * </p>
     *
     * @param requestParamCount  请求参数数量
     * @param responseParamCount 响应字段数量
     */
    public void validateParamCounts(long requestParamCount, long responseParamCount) {
        validateCounts(requestParamCount, responseParamCount, 0);
    }

    /**
     * 校验自动生成的运行时参数示例值长度。
     *
     * @param exampleValue 示例值
     */
    public void validateRuntimeExampleValue(String exampleValue) {
        assertUnicodeLength(exampleValue, MAX_PARAM_EXAMPLE_VALUE_LENGTH,
                "参数示例值长度不能超过 1024 个字符");
    }

    /**
     * 校验运行时参数名称长度。
     *
     * @param name 参数名称
     */
    public void validateRuntimeParamName(String name) {
        assertUnicodeLength(name, MAX_PARAM_NAME_LENGTH, "参数名称长度不能超过 128 个字符");
    }

    /**
     * 校验请求、响应和错误码分类数量。
     *
     * @param requestParamCount  请求参数数量
     * @param responseParamCount 响应字段数量
     * @param errorCodeCount     错误码数量
     */
    private void validateCounts(long requestParamCount, long responseParamCount, long errorCodeCount) {
        if (requestParamCount > MAX_REQUEST_PARAM_COUNT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数数量不能超过 100");
        }
        if (responseParamCount > MAX_RESPONSE_PARAM_COUNT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "响应字段数量不能超过 200");
        }
        if (requestParamCount + responseParamCount > MAX_TOTAL_PARAM_COUNT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数与响应字段合计数量不能超过 200");
        }
        if (errorCodeCount > MAX_ERROR_CODE_COUNT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "错误码数量不能超过 100");
        }
    }

    /**
     * 校验文档参数保存请求的文本边界。
     *
     * @param request 参数保存请求
     */
    private void validateParamRequest(InterfaceDocParamSaveRequest request) {
        assertUnicodeLength(request.getName(), MAX_PARAM_NAME_LENGTH, "参数名称长度不能超过 128 个字符");
        assertUnicodeLength(request.getDefaultValue(), MAX_PARAM_DEFAULT_VALUE_LENGTH,
                "参数默认值长度不能超过 512 个字符");
        assertUnicodeLength(request.getExampleValue(), MAX_PARAM_EXAMPLE_VALUE_LENGTH,
                "参数示例值长度不能超过 1024 个字符");
        assertUnicodeLength(request.getDescription(), MAX_DESCRIPTION_LENGTH,
                "参数说明长度不能超过 512 个字符");
        assertUnicodeLength(request.getValidationRule(), MAX_DESCRIPTION_LENGTH,
                "校验规则长度不能超过 512 个字符");
    }

    /**
     * 校验错误码保存请求的文本边界。
     *
     * @param request 错误码保存请求
     */
    private void validateErrorCodeRequest(InterfaceDocErrorCodeSaveRequest request) {
        assertUnicodeLength(request.getErrorCode(), MAX_ERROR_CODE_LENGTH, "错误码长度不能超过 64 个字符");
        assertUnicodeLength(request.getErrorMessage(), MAX_ERROR_MESSAGE_LENGTH,
                "错误信息长度不能超过 256 个字符");
        assertUnicodeLength(request.getDescription(), MAX_DESCRIPTION_LENGTH,
                "错误说明长度不能超过 512 个字符");
        assertUnicodeLength(request.getSolution(), MAX_DESCRIPTION_LENGTH,
                "解决建议长度不能超过 512 个字符");
    }

    /**
     * 校验持久化参数的文本边界。
     *
     * @param param 参数实体
     */
    private void validatePersistedParam(InterfaceDocParam param) {
        assertUnicodeLength(param.getName(), MAX_PARAM_NAME_LENGTH, "参数名称长度不能超过 128 个字符");
        assertUnicodeLength(param.getDefaultValue(), MAX_PARAM_DEFAULT_VALUE_LENGTH,
                "参数默认值长度不能超过 512 个字符");
        assertUnicodeLength(param.getExampleValue(), MAX_PARAM_EXAMPLE_VALUE_LENGTH,
                "参数示例值长度不能超过 1024 个字符");
        assertUnicodeLength(param.getDescription(), MAX_DESCRIPTION_LENGTH,
                "参数说明长度不能超过 512 个字符");
        assertUnicodeLength(param.getValidationRule(), MAX_DESCRIPTION_LENGTH,
                "校验规则长度不能超过 512 个字符");
    }

    /**
     * 校验持久化错误码的文本边界。
     *
     * @param errorCode 错误码实体
     */
    private void validatePersistedErrorCode(InterfaceDocErrorCode errorCode) {
        assertUnicodeLength(errorCode.getErrorCode(), MAX_ERROR_CODE_LENGTH, "错误码长度不能超过 64 个字符");
        assertUnicodeLength(errorCode.getErrorMessage(), MAX_ERROR_MESSAGE_LENGTH,
                "错误信息长度不能超过 256 个字符");
        assertUnicodeLength(errorCode.getDescription(), MAX_DESCRIPTION_LENGTH,
                "错误说明长度不能超过 512 个字符");
        assertUnicodeLength(errorCode.getSolution(), MAX_DESCRIPTION_LENGTH,
                "解决建议长度不能超过 512 个字符");
    }

    /**
     * 校验去除首尾空白后的 Unicode 码点长度。
     *
     * @param value        待校验文本
     * @param max          最大码点数量
     * @param errorMessage 错误消息
     */
    private void assertUnicodeLength(String value, int max, String errorMessage) {
        if (TextSizeUtils.unicodeLengthAfterStrip(value) > max) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, errorMessage);
        }
    }

    /**
     * 校验 UTF-8 实际字节数。
     *
     * @param value        待校验文本
     * @param max          最大字节数
     * @param errorMessage 错误消息
     */
    private void assertUtf8ByteLength(String value, int max, String errorMessage) {
        if (TextSizeUtils.utf8ByteLength(value) > max) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, errorMessage);
        }
    }

    /**
     * 统计保存请求中的运行时请求参数数量。
     *
     * @param params 参数请求列表
     * @return 请求参数数量
     */
    private long countRequestParams(List<InterfaceDocParamSaveRequest> params) {
        return params == null ? 0 : params.stream()
                .filter(Objects::nonNull)
                .filter(param -> InterfaceDocParamSceneEnum.QUERY.getValue().equals(param.getParamScene())
                        || InterfaceDocParamSceneEnum.BODY.getValue().equals(param.getParamScene()))
                .count();
    }

    /**
     * 统计保存请求中的响应字段数量。
     *
     * @param params 参数请求列表
     * @return 响应字段数量
     */
    private long countResponseParams(List<InterfaceDocParamSaveRequest> params) {
        return params == null ? 0 : params.stream()
                .filter(Objects::nonNull)
                .filter(param -> InterfaceDocParamSceneEnum.RESPONSE.getValue().equals(param.getParamScene()))
                .count();
    }

    /**
     * 统计持久化请求参数数量。
     *
     * @param params 参数实体列表
     * @return 请求参数数量
     */
    private long countPersistedRequestParams(List<InterfaceDocParam> params) {
        return params == null ? 0 : params.stream()
                .filter(Objects::nonNull)
                .filter(param -> InterfaceDocParamSceneEnum.QUERY.getValue().equals(param.getParamScene())
                        || InterfaceDocParamSceneEnum.BODY.getValue().equals(param.getParamScene()))
                .count();
    }

    /**
     * 统计持久化响应字段数量。
     *
     * @param params 参数实体列表
     * @return 响应字段数量
     */
    private long countPersistedResponseParams(List<InterfaceDocParam> params) {
        return params == null ? 0 : params.stream()
                .filter(Objects::nonNull)
                .filter(param -> InterfaceDocParamSceneEnum.RESPONSE.getValue().equals(param.getParamScene()))
                .count();
    }

    /**
     * 返回空安全的集合大小。
     *
     * @param values 集合
     * @return 集合大小
     */
    private int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
