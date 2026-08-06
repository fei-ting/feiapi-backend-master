package com.feiting.feiapi.unit.service;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.interfaceplatform.definition.component.SdkMethodRegistry;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.InterfaceDefinitionSnapshot;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.RuntimeRequestTemplate;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.SdkContractSnapshot;
import com.feiting.feiapi.interfaceplatform.definition.service.impl.InterfaceDefinitionReaderImpl;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapiclientsdk.client.FeiApiClient;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口定义域只读服务适配实现单元测试。
 */
@DisplayName("接口定义域只读服务适配实现单元测试")
class InterfaceDefinitionReaderImplTest {

    /**
     * 接口信息 ID。
     */
    private static final Long INTERFACE_INFO_ID = 1001L;

    /**
     * 既有接口信息服务。
     */
    private InterfaceInfoService interfaceInfoService;

    /**
     * 既有 SDK 方法注册器。
     */
    private SdkMethodRegistry sdkMethodRegistry;

    /**
     * 被测接口定义域只读服务。
     */
    private InterfaceDefinitionReaderImpl reader;

    /**
     * 初始化测试依赖。
     */
    @BeforeEach
    void setUp() {
        interfaceInfoService = mock(InterfaceInfoService.class);
        sdkMethodRegistry = mock(SdkMethodRegistry.class);
        reader = new InterfaceDefinitionReaderImpl(interfaceInfoService, sdkMethodRegistry);
    }

    /**
     * 必要接口存在时返回完整定义快照。
     */
    @Test
    @DisplayName("接口存在时返回完整定义快照")
    void shouldReturnDefinitionSnapshotWhenInterfaceExists() {
        InterfaceInfo interfaceInfo = buildInterfaceInfo();
        when(interfaceInfoService.getById(INTERFACE_INFO_ID)).thenReturn(interfaceInfo);

        InterfaceDefinitionSnapshot snapshot = reader.getRequiredSnapshot(INTERFACE_INFO_ID);

        assertThat(snapshot.getInterfaceInfoId()).isEqualTo(INTERFACE_INFO_ID);
        assertThat(snapshot.getName()).isEqualTo("随机情话");
        assertThat(snapshot.getSdkMethodName()).isEqualTo("getLoveWords");
        assertThat(snapshot.getDescription()).isEqualTo("获取一句随机情话");
        assertThat(snapshot.getUrl()).isEqualTo("http://example.com/api/love");
        assertThat(snapshot.getPath()).isEqualTo("/api/love");
        assertThat(snapshot.getTargetHost()).isEqualTo("http://localhost:8090");
        assertThat(snapshot.getRequestParams()).isEqualTo("{\"keyword\":\"string\"}");
        assertThat(snapshot.getRequestHeader()).isEqualTo("Authorization");
        assertThat(snapshot.getResponseHeader()).isEqualTo("Content-Type");
        assertThat(snapshot.getStatus()).isEqualTo(0);
        assertThat(snapshot.getMethod()).isEqualTo("GET");
        assertThat(snapshot.getQuotaType()).isEqualTo("BASIC_QUOTA");
        assertThat(snapshot.getUserId()).isEqualTo(10L);
        assertThat(snapshot.getCreateTime()).isEqualTo(interfaceInfo.getCreateTime());
        assertThat(snapshot.getUpdateTime()).isEqualTo(interfaceInfo.getUpdateTime());
    }

    /**
     * 接口不存在时抛出既有未找到业务异常。
     */
    @Test
    @DisplayName("接口不存在时抛出未找到异常")
    void shouldThrowWhenInterfaceMissing() {
        when(interfaceInfoService.getById(INTERFACE_INFO_ID)).thenReturn(null);

        assertThatThrownBy(() -> reader.getRequiredSnapshot(INTERFACE_INFO_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_FOUND_ERROR.getCode());
    }

    /**
     * 接口 ID 非法时不访问底层服务并抛出参数异常。
     */
    @Test
    @DisplayName("接口 ID 非法时抛出参数异常")
    void shouldThrowWhenInterfaceInfoIdInvalid() {
        assertThatThrownBy(() -> reader.getRequiredSnapshot(0L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PARAMS_ERROR.getCode());
        verify(interfaceInfoService, never()).getById(0L);
    }

    /**
     * 运行时请求参数模板快照只包含同步所需字段。
     */
    @Test
    @DisplayName("返回运行时请求参数模板快照")
    void shouldReturnRuntimeRequestTemplate() {
        InterfaceInfo interfaceInfo = buildInterfaceInfo();
        when(interfaceInfoService.getById(INTERFACE_INFO_ID)).thenReturn(interfaceInfo);

        RuntimeRequestTemplate template = reader.getRuntimeRequestTemplate(INTERFACE_INFO_ID);

        assertThat(template.getInterfaceInfoId()).isEqualTo(INTERFACE_INFO_ID);
        assertThat(template.getMethod()).isEqualTo("GET");
        assertThat(template.getRequestParams()).isEqualTo("{\"keyword\":\"string\"}");
    }

    /**
     * SDK 方法存在时返回已注册契约。
     *
     * @throws NoSuchMethodException 反射方法不存在时抛出
     */
    @Test
    @DisplayName("SDK 方法存在时返回契约快照")
    void shouldReturnSupportedSdkContract() throws NoSuchMethodException {
        Method method = FeiApiClient.class.getDeclaredMethod("getUsernameByPost", String.class);
        when(sdkMethodRegistry.getMethodMap()).thenReturn(Map.of("getUsernameByPost", method));

        SdkContractSnapshot snapshot = reader.getSdkContract(" getUsernameByPost ");

        assertThat(snapshot.getSdkMethodName()).isEqualTo("getUsernameByPost");
        assertThat(snapshot.isSupported()).isTrue();
        assertThat(snapshot.isNeedParams()).isTrue();
        assertThat(snapshot.getParameterCount()).isEqualTo(1);
        assertThat(snapshot.getReturnTypeName()).isEqualTo(String.class.getName());
    }

    /**
     * SDK 方法不存在时返回不支持契约而不抛异常。
     */
    @Test
    @DisplayName("SDK 方法不存在时返回不支持契约快照")
    void shouldReturnUnsupportedSdkContractWhenMethodMissing() {
        when(sdkMethodRegistry.getMethodMap()).thenReturn(Map.of());

        SdkContractSnapshot snapshot = reader.getSdkContract(" missingMethod ");

        assertThat(snapshot.getSdkMethodName()).isEqualTo("missingMethod");
        assertThat(snapshot.isSupported()).isFalse();
        assertThat(snapshot.isNeedParams()).isFalse();
        assertThat(snapshot.getParameterCount()).isZero();
        assertThat(snapshot.getReturnTypeName()).isEmpty();
    }

    /**
     * 已注册 SDK 方法列表应按方法名排序并返回参数契约。
     *
     * @throws NoSuchMethodException 反射方法不存在时抛出
     */
    @Test
    @DisplayName("SDK 方法列表按方法名排序")
    void shouldListSdkContractsInMethodNameOrder() throws NoSuchMethodException {
        Method usernameMethod = FeiApiClient.class.getDeclaredMethod("getUsernameByPost", String.class);
        Method loveWordsMethod = FeiApiClient.class.getDeclaredMethod("getLoveWords");
        when(sdkMethodRegistry.getMethodMap()).thenReturn(Map.of(
                "getUsernameByPost", usernameMethod,
                "getLoveWords", loveWordsMethod));

        List<SdkContractSnapshot> contracts = reader.listSdkContracts();

        assertThat(contracts)
                .extracting(SdkContractSnapshot::getSdkMethodName)
                .containsExactly("getLoveWords", "getUsernameByPost");
        assertThat(contracts.get(0).isNeedParams()).isFalse();
        assertThat(contracts.get(1).isNeedParams()).isTrue();
    }

    /**
     * 构造接口信息实体。
     *
     * @return 接口信息实体
     */
    private InterfaceInfo buildInterfaceInfo() {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setId(INTERFACE_INFO_ID);
        interfaceInfo.setName("随机情话");
        interfaceInfo.setSdkMethodName("getLoveWords");
        interfaceInfo.setDescription("获取一句随机情话");
        interfaceInfo.setUrl("http://example.com/api/love");
        interfaceInfo.setPath("/api/love");
        interfaceInfo.setTargetHost("http://localhost:8090");
        interfaceInfo.setRequestParams("{\"keyword\":\"string\"}");
        interfaceInfo.setRequestHeader("Authorization");
        interfaceInfo.setResponseHeader("Content-Type");
        interfaceInfo.setStatus(0);
        interfaceInfo.setMethod("GET");
        interfaceInfo.setQuotaType("BASIC_QUOTA");
        interfaceInfo.setUserId(10L);
        interfaceInfo.setCreateTime(new Date(1000L));
        interfaceInfo.setUpdateTime(new Date(2000L));
        return interfaceInfo;
    }
}
