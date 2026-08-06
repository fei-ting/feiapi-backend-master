package com.feiting.feiapi.unit.component;

import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocBoundaryValidator;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.documentation.model.dto.interfaceDoc.InterfaceDocErrorCodeSaveRequest;
import com.feiting.feiapi.interfaceplatform.documentation.model.dto.interfaceDoc.InterfaceDocParamSaveRequest;
import com.feiting.feiapi.interfaceplatform.documentation.model.dto.interfaceDoc.InterfaceDocSaveRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 接口文档数量与文本边界校验器测试。
 */
@DisplayName("InterfaceDocBoundaryValidator 测试")
class InterfaceDocBoundaryValidatorTest {

    /** 被测边界校验器。 */
    private final InterfaceDocBoundaryValidator validator = new InterfaceDocBoundaryValidator();

    /**
     * 校验请求参数与响应字段合计数量边界。
     */
    @Test
    @DisplayName("参数合计 200 允许且 201 拒绝")
    void shouldValidateCombinedParamCount() {
        InterfaceDocSaveRequest allowed = buildBasicRequest();
        allowed.setParams(buildParams(100, 100));
        InterfaceDocSaveRequest rejected = buildBasicRequest();
        rejected.setParams(buildParams(100, 101));

        assertThatCode(() -> validator.validateSaveRequest(allowed)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateSaveRequest(rejected))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请求参数与响应字段合计数量不能超过 200");
    }

    /**
     * 校验错误码数量边界。
     */
    @Test
    @DisplayName("错误码 100 允许且 101 拒绝")
    void shouldValidateErrorCodeCount() {
        InterfaceDocSaveRequest allowed = buildBasicRequest();
        allowed.setErrorCodes(buildErrorCodes(100));
        InterfaceDocSaveRequest rejected = buildBasicRequest();
        rejected.setErrorCodes(buildErrorCodes(101));

        assertThatCode(() -> validator.validateSaveRequest(allowed)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateSaveRequest(rejected))
                .isInstanceOf(BusinessException.class)
                .hasMessage("错误码数量不能超过 100");
    }

    /**
     * 校验 JSON 示例按 UTF-8 字节数执行边界判断。
     */
    @Test
    @DisplayName("JSON 示例允许 65535 字节并拒绝 65536 字节")
    void shouldValidateJsonExampleUtf8Bytes() {
        InterfaceDocSaveRequest allowed = buildBasicRequest();
        allowed.setSuccessExample("a".repeat(65535));
        InterfaceDocSaveRequest rejected = buildBasicRequest();
        rejected.setSuccessExample("a".repeat(65536));

        assertThatCode(() -> validator.validateSaveRequest(allowed)).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateSaveRequest(rejected))
                .isInstanceOf(BusinessException.class)
                .hasMessage("成功响应示例不能超过 65535 个 UTF-8 字节");
    }

    /**
     * 构建基础聚合保存请求。
     *
     * @return 基础保存请求
     */
    private InterfaceDocSaveRequest buildBasicRequest() {
        InterfaceDocSaveRequest request = new InterfaceDocSaveRequest();
        request.setParams(new ArrayList<>());
        request.setErrorCodes(new ArrayList<>());
        return request;
    }

    /**
     * 构建指定数量的请求参数与响应字段。
     *
     * @param requestCount  请求参数数量
     * @param responseCount 响应字段数量
     * @return 参数列表
     */
    private List<InterfaceDocParamSaveRequest> buildParams(int requestCount, int responseCount) {
        List<InterfaceDocParamSaveRequest> params = IntStream.range(0, requestCount)
                .mapToObj(index -> buildParam("BODY", "request" + index))
                .collect(Collectors.toCollection(ArrayList::new));
        params.addAll(IntStream.range(0, responseCount)
                .mapToObj(index -> buildParam("RESPONSE", "response" + index))
                .collect(Collectors.toList()));
        return params;
    }

    /**
     * 构建文档参数请求。
     *
     * @param scene 参数场景
     * @param name  参数名称
     * @return 参数请求
     */
    private InterfaceDocParamSaveRequest buildParam(String scene, String name) {
        InterfaceDocParamSaveRequest param = new InterfaceDocParamSaveRequest();
        param.setParamScene(scene);
        param.setName(name);
        return param;
    }

    /**
     * 构建指定数量的错误码请求。
     *
     * @param count 错误码数量
     * @return 错误码列表
     */
    private List<InterfaceDocErrorCodeSaveRequest> buildErrorCodes(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> {
                    InterfaceDocErrorCodeSaveRequest errorCode = new InterfaceDocErrorCodeSaveRequest();
                    errorCode.setErrorCode("E" + index);
                    errorCode.setErrorMessage("错误" + index);
                    return errorCode;
                })
                .collect(Collectors.toList());
    }
}
