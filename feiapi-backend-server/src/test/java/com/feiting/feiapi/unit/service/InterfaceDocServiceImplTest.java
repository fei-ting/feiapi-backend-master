package com.feiting.feiapi.unit.service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.feiting.feiapi.component.InterfaceDocContentSecurityValidator;
import com.feiting.feiapi.component.InterfaceDocBoundaryValidator;
import com.feiting.feiapi.component.InterfaceDocCurlExampleGenerator;
import com.feiting.feiapi.component.InterfaceDocJavaSdkExampleGenerator;
import com.feiting.feiapi.component.RuntimeRequestParamTemplateValidator;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.mapper.InterfaceDocMapper;
import com.feiting.feiapi.mapper.InterfaceInfoMapper;
import com.feiting.feiapi.model.entity.InterfaceDocErrorCode;
import com.feiting.feiapi.model.entity.InterfaceDoc;
import com.feiting.feiapi.model.entity.InterfaceDocParam;
import com.feiting.feiapi.model.vo.InterfaceDocDetailVO;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import com.feiting.feiapi.service.InterfaceDocParamService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfaceQuotaConfigService;
import com.feiting.feiapi.service.UserInterfaceInfoService;
import com.feiting.feiapi.service.impl.InterfaceDocServiceImpl;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 接口文档服务实现单元测试。
 */
@DisplayName("接口文档服务实现单元测试")
class InterfaceDocServiceImplTest {

    /** 测试接口信息 ID。 */
    private static final long INTERFACE_INFO_ID = 1L;

    /** 接口信息服务。 */
    private InterfaceInfoService interfaceInfoService;

    /** 接口信息数据访问对象。 */
    private InterfaceInfoMapper interfaceInfoMapper;

    /** 文档参数服务。 */
    private InterfaceDocParamService interfaceDocParamService;

    /** 文档错误码服务。 */
    private InterfaceDocErrorCodeService interfaceDocErrorCodeService;

    /** 接口配额配置服务。 */
    private InterfaceQuotaConfigService interfaceQuotaConfigService;

    /** 用户接口调用关系服务。 */
    private UserInterfaceInfoService userInterfaceInfoService;

    /** 文档内容安全校验器。 */
    private InterfaceDocContentSecurityValidator contentSecurityValidator;

    /** curl 示例生成器。 */
    private InterfaceDocCurlExampleGenerator curlExampleGenerator;

    /** Java SDK 示例生成器。 */
    private InterfaceDocJavaSdkExampleGenerator javaSdkExampleGenerator;

    /** 运行时请求参数模板校验器。 */
    private RuntimeRequestParamTemplateValidator runtimeRequestParamTemplateValidator;

    /** 文档数量与文本边界校验器。 */
    private InterfaceDocBoundaryValidator boundaryValidator;

    /** 被测服务。 */
    private InterfaceDocServiceImpl docService;

    /** 初始化被测服务及其依赖。 */
    @BeforeEach
    void setUp() {
        interfaceInfoService = mock(InterfaceInfoService.class);
        interfaceInfoMapper = mock(InterfaceInfoMapper.class);
        interfaceDocParamService = mock(InterfaceDocParamService.class);
        interfaceDocErrorCodeService = mock(InterfaceDocErrorCodeService.class);
        interfaceQuotaConfigService = mock(InterfaceQuotaConfigService.class);
        userInterfaceInfoService = mock(UserInterfaceInfoService.class);
        contentSecurityValidator = mock(InterfaceDocContentSecurityValidator.class);
        curlExampleGenerator = mock(InterfaceDocCurlExampleGenerator.class);
        javaSdkExampleGenerator = mock(InterfaceDocJavaSdkExampleGenerator.class);
        runtimeRequestParamTemplateValidator = mock(RuntimeRequestParamTemplateValidator.class);
        boundaryValidator = mock(InterfaceDocBoundaryValidator.class);
        docService = spy(new InterfaceDocServiceImpl(
                interfaceInfoService,
                interfaceInfoMapper,
                interfaceDocParamService,
                interfaceDocErrorCodeService,
                interfaceQuotaConfigService,
                userInterfaceInfoService,
                contentSecurityValidator,
                curlExampleGenerator,
                javaSdkExampleGenerator,
                runtimeRequestParamTemplateValidator,
                boundaryValidator));

        ReflectionTestUtils.setField(docService, "gatewayHost", "http://gateway");
        LambdaQueryChainWrapper<InterfaceDoc> docQuery = mock(LambdaQueryChainWrapper.class);
        doReturn(docQuery).when(docService).lambdaQuery();
        when(docQuery.eq(any(), any())).thenReturn(docQuery);
        when(docQuery.one()).thenReturn(null);
        stubEmptyDocumentQueries();
        when(userInterfaceInfoService.listTotalNumByInterfaceInfoIds(any())).thenReturn(Collections.emptyMap());
        when(curlExampleGenerator.generate(any(InterfaceInfo.class), any(InterfaceDocDetailVO.class)))
                .thenReturn("curl example");
    }

    /** SDK 方法名为空时，详情查询仍应返回基础详情与 curl 示例。 */
    @Test
    @DisplayName("SDK 方法名为空时不影响详情查询")
    void shouldKeepDocDetailWhenSdkMethodNameIsBlank() {
        InterfaceInfo interfaceInfo = buildInterfaceInfo(null);
        when(interfaceInfoService.getById(INTERFACE_INFO_ID)).thenReturn(interfaceInfo);
        when(javaSdkExampleGenerator.generate(any(InterfaceInfo.class), any(List.class)))
                .thenThrow(new BusinessException(com.feiting.feiapi.common.ErrorCode.PARAMS_ERROR, "SDK 方法名不能为空"));

        InterfaceDocDetailVO detailVO = docService.getDocDetail(INTERFACE_INFO_ID, true);

        assertThat(detailVO.getInterfaceInfo().getId()).isEqualTo(INTERFACE_INFO_ID);
        assertThat(detailVO.getJavaSdkExample()).isEmpty();
        assertThat(detailVO.getCurlExample()).isEqualTo("curl example");
    }

    /** SDK 方法未注册时，详情查询仍应返回基础详情与 curl 示例。 */
    @Test
    @DisplayName("SDK 方法未注册时不影响详情查询")
    void shouldKeepDocDetailWhenSdkMethodNameIsUnknown() {
        InterfaceInfo interfaceInfo = buildInterfaceInfo("unknownSdkMethod");
        when(interfaceInfoService.getById(INTERFACE_INFO_ID)).thenReturn(interfaceInfo);
        when(javaSdkExampleGenerator.generate(any(InterfaceInfo.class), any(List.class)))
                .thenThrow(new BusinessException(com.feiting.feiapi.common.ErrorCode.PARAMS_ERROR,
                        "不支持的接口方法：unknownSdkMethod"));

        InterfaceDocDetailVO detailVO = docService.getDocDetail(INTERFACE_INFO_ID, true);

        assertThat(detailVO.getInterfaceInfo().getName()).isEqualTo("测试接口");
        assertThat(detailVO.getJavaSdkExample()).isEmpty();
        assertThat(detailVO.getCurlExample()).isEqualTo("curl example");
    }

    /** 构造最小可查询接口信息。 */
    private InterfaceInfo buildInterfaceInfo(String sdkMethodName) {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(INTERFACE_INFO_ID);
        interfaceInfo.setName("测试接口");
        interfaceInfo.setSdkMethodName(sdkMethodName);
        interfaceInfo.setMethod("GET");
        interfaceInfo.setPath("/test");
        return interfaceInfo;
    }

    /** Stub 文档主信息、参数和错误码的空查询结果。 */
    @SuppressWarnings("unchecked")
    private void stubEmptyDocumentQueries() {
        LambdaQueryChainWrapper<InterfaceDocParam> paramQuery = mock(LambdaQueryChainWrapper.class);
        when(interfaceDocParamService.lambdaQuery()).thenReturn(paramQuery);
        when(paramQuery.eq(any(), any())).thenReturn(paramQuery);
        when(paramQuery.orderByAsc((SFunction<InterfaceDocParam, ?>) any())).thenReturn(paramQuery);
        when(paramQuery.list()).thenReturn(Collections.emptyList());

        LambdaQueryChainWrapper<InterfaceDocErrorCode> errorCodeQuery = mock(LambdaQueryChainWrapper.class);
        when(interfaceDocErrorCodeService.lambdaQuery()).thenReturn(errorCodeQuery);
        when(errorCodeQuery.eq(any(), any())).thenReturn(errorCodeQuery);
        when(errorCodeQuery.orderByAsc((SFunction<InterfaceDocErrorCode, ?>) any())).thenReturn(errorCodeQuery);
        when(errorCodeQuery.list()).thenReturn(Collections.emptyList());
    }
}
