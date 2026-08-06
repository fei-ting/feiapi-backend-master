package com.feiting.feiapi.unit.component;

import com.feiting.feiapi.interfaceplatform.definition.component.InterfaceDefinitionChangeDetector;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 接口定义变更检测器测试。
 */
@DisplayName("接口定义变更检测器测试")
class InterfaceDefinitionChangeDetectorTest {

    /**
     * 被测接口定义变更检测器。
     */
    private final InterfaceDefinitionChangeDetector detector = new InterfaceDefinitionChangeDetector();

    /**
     * 请求方法变化时应触发请求文档模板变化。
     */
    @Test
    @DisplayName("请求方法变化时识别为模板变化")
    void shouldDetectMethodChangedAsRequestDocTemplateChanged() {
        InterfaceInfo oldInterfaceInfo = buildBaseInterfaceInfo();
        InterfaceInfo latestInterfaceInfo = buildBaseInterfaceInfo();
        latestInterfaceInfo.setMethod("POST");

        assertThat(detector.requestDocTemplateChanged(oldInterfaceInfo, latestInterfaceInfo)).isTrue();
    }

    /**
     * 运行时请求参数模板变化时应触发请求文档模板变化。
     */
    @Test
    @DisplayName("运行时请求参数模板变化时识别为模板变化")
    void shouldDetectRequestParamsChangedAsRequestDocTemplateChanged() {
        InterfaceInfo oldInterfaceInfo = buildBaseInterfaceInfo();
        InterfaceInfo latestInterfaceInfo = buildBaseInterfaceInfo();
        latestInterfaceInfo.setRequestParams("{\"name\":\"string\"}");

        assertThat(detector.requestDocTemplateChanged(oldInterfaceInfo, latestInterfaceInfo)).isTrue();
    }

    /**
     * 非模板字段变化时不触发请求文档模板变化。
     */
    @Test
    @DisplayName("非模板字段变化时不识别为模板变化")
    void shouldNotDetectNonTemplateFieldAsRequestDocTemplateChanged() {
        InterfaceInfo oldInterfaceInfo = buildBaseInterfaceInfo();
        InterfaceInfo latestInterfaceInfo = buildBaseInterfaceInfo();
        latestInterfaceInfo.setName("新的接口名称");

        assertThat(detector.requestDocTemplateChanged(oldInterfaceInfo, latestInterfaceInfo)).isFalse();
    }

    /**
     * 受控配置字段变化时应触发文档降级判断。
     */
    @Test
    @DisplayName("受控配置字段变化时识别为有效变化")
    void shouldDetectControlledConfigChanged() {
        InterfaceInfo oldInterfaceInfo = buildBaseInterfaceInfo();
        InterfaceInfo latestInterfaceInfo = buildBaseInterfaceInfo();
        latestInterfaceInfo.setTargetHost("http://localhost:8091");

        assertThat(detector.controlledConfigChanged(oldInterfaceInfo, latestInterfaceInfo)).isTrue();
    }

    /**
     * 非受控字段变化时不触发文档降级判断。
     */
    @Test
    @DisplayName("非受控字段变化时不识别为有效变化")
    void shouldIgnoreUncontrolledConfigChanged() {
        InterfaceInfo oldInterfaceInfo = buildBaseInterfaceInfo();
        InterfaceInfo latestInterfaceInfo = buildBaseInterfaceInfo();
        latestInterfaceInfo.setStatus(1);

        assertThat(detector.controlledConfigChanged(oldInterfaceInfo, latestInterfaceInfo)).isFalse();
    }

    /**
     * 构建基础接口信息。
     *
     * @return 基础接口信息
     */
    private InterfaceInfo buildBaseInterfaceInfo() {
        InterfaceInfo interfaceInfo = new InterfaceInfo();
        interfaceInfo.setName("随机情话");
        interfaceInfo.setDescription("获取一句随机情话");
        interfaceInfo.setMethod("GET");
        interfaceInfo.setPath("/api/love");
        interfaceInfo.setTargetHost("http://localhost:8090");
        interfaceInfo.setUrl("http://localhost:8090/api/love");
        interfaceInfo.setQuotaType("BASIC_QUOTA");
        interfaceInfo.setSdkMethodName("getLoveWords");
        interfaceInfo.setRequestParams("{\"keyword\":\"string\"}");
        interfaceInfo.setStatus(0);
        return interfaceInfo;
    }
}
