package com.feiting.feiapi.service.impl;

import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocBoundaryValidator;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocContentSecurityValidator;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocCurlExampleGenerator;
import com.feiting.feiapi.interfaceplatform.documentation.component.InterfaceDocJavaSdkExampleGenerator;
import com.feiting.feiapi.interfaceplatform.documentation.service.impl.InterfaceDocFacadeServiceImpl;
import com.feiting.feiapi.interfaceplatform.definition.component.RuntimeRequestParamTemplateValidator;
import com.feiting.feiapi.mapper.InterfaceInfoMapper;
import com.feiting.feiapi.service.InterfaceDocErrorCodeService;
import com.feiting.feiapi.service.InterfaceDocParamService;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapi.service.InterfaceQuotaConfigService;

/**
 * 接口文档旧包兼容实现。
 *
 * <p>该类仅用于兼容历史测试和内部直接构造，不注册为 Spring Bean。</p>
 */
public class InterfaceDocServiceImpl extends InterfaceDocFacadeServiceImpl {

    /**
     * 创建接口文档兼容实现。
     *
     * @param interfaceInfoService                接口信息服务
     * @param interfaceInfoMapper                 接口信息数据访问对象
     * @param interfaceDocParamService            文档参数服务
     * @param interfaceDocErrorCodeService        文档错误码服务
     * @param interfaceQuotaConfigService         接口配额配置服务
     * @param userInterfaceInfoService             用户接口调用关系服务
     * @param contentSecurityValidator            文档内容安全校验器
     * @param curlExampleGenerator                curl 示例生成器
     * @param javaSdkExampleGenerator             Java SDK 示例生成器
     * @param runtimeRequestParamTemplateValidator 运行时请求参数模板校验器
     * @param boundaryValidator                   文档边界校验器
     */
    public InterfaceDocServiceImpl(InterfaceInfoService interfaceInfoService,
                                   InterfaceInfoMapper interfaceInfoMapper,
                                   InterfaceDocParamService interfaceDocParamService,
                                   InterfaceDocErrorCodeService interfaceDocErrorCodeService,
                                   InterfaceQuotaConfigService interfaceQuotaConfigService,
                                   com.feiting.feiapi.service.UserInterfaceInfoService userInterfaceInfoService,
                                   InterfaceDocContentSecurityValidator contentSecurityValidator,
                                   InterfaceDocCurlExampleGenerator curlExampleGenerator,
                                   InterfaceDocJavaSdkExampleGenerator javaSdkExampleGenerator,
                                   RuntimeRequestParamTemplateValidator runtimeRequestParamTemplateValidator,
                                   InterfaceDocBoundaryValidator boundaryValidator) {
        super(interfaceInfoService, interfaceInfoMapper, interfaceDocParamService, interfaceDocErrorCodeService,
                interfaceQuotaConfigService, userInterfaceInfoService, contentSecurityValidator,
                curlExampleGenerator, javaSdkExampleGenerator, runtimeRequestParamTemplateValidator,
                boundaryValidator);
    }
}
