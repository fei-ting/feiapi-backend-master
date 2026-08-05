package com.feiting.feiapi.interfaceplatform.definition.service.impl;

import com.feiting.feiapi.common.ErrorCode;
import com.feiting.feiapi.exception.BusinessException;
import com.feiting.feiapi.interfaceplatform.definition.component.SdkMethodRegistry;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.InterfaceDefinitionSnapshot;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.RuntimeRequestTemplate;
import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.SdkContractSnapshot;
import com.feiting.feiapi.interfaceplatform.definition.service.api.InterfaceDefinitionReader;
import com.feiting.feiapi.service.InterfaceInfoService;
import com.feiting.feiapiclientsdk.annotation.SdkInvoke;
import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;

/**
 * 接口定义域只读服务适配实现。
 *
 * <p>当前阶段仅适配既有接口信息服务和 SDK 方法注册器，不改变原有调用链和业务写入路径。</p>
 */
@Service
public class InterfaceDefinitionReaderImpl implements InterfaceDefinitionReader {

    /**
     * 既有接口信息服务。
     */
    private final InterfaceInfoService interfaceInfoService;

    /**
     * 既有 SDK 方法注册器。
     */
    private final SdkMethodRegistry sdkMethodRegistry;

    /**
     * 创建接口定义域只读服务适配实现。
     *
     * @param interfaceInfoService 既有接口信息服务
     * @param sdkMethodRegistry    既有 SDK 方法注册器
     */
    public InterfaceDefinitionReaderImpl(InterfaceInfoService interfaceInfoService,
                                         SdkMethodRegistry sdkMethodRegistry) {
        this.interfaceInfoService = interfaceInfoService;
        this.sdkMethodRegistry = sdkMethodRegistry;
    }

    /**
     * 获取必然存在的接口定义快照。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 接口定义快照
     */
    @Override
    public InterfaceDefinitionSnapshot getRequiredSnapshot(Long interfaceInfoId) {
        InterfaceInfo interfaceInfo = getRequiredInterfaceInfo(interfaceInfoId);
        return toDefinitionSnapshot(interfaceInfo);
    }

    /**
     * 获取运行时请求参数模板快照。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 运行时请求参数模板快照
     */
    @Override
    public RuntimeRequestTemplate getRuntimeRequestTemplate(Long interfaceInfoId) {
        InterfaceInfo interfaceInfo = getRequiredInterfaceInfo(interfaceInfoId);
        return RuntimeRequestTemplate.builder()
                .interfaceInfoId(interfaceInfo.getId())
                .method(interfaceInfo.getMethod())
                .requestParams(interfaceInfo.getRequestParams())
                .build();
    }

    /**
     * 获取 SDK 方法契约快照。
     *
     * @param sdkMethodName SDK 方法名
     * @return SDK 方法契约快照
     */
    @Override
    public SdkContractSnapshot getSdkContract(String sdkMethodName) {
        String normalizedMethodName = StringUtils.trimToEmpty(sdkMethodName);
        Method method = sdkMethodRegistry.getMethodMap().get(normalizedMethodName);
        if (method == null) {
            return SdkContractSnapshot.builder()
                    .sdkMethodName(normalizedMethodName)
                    .supported(false)
                    .needParams(false)
                    .parameterCount(0)
                    .returnTypeName("")
                    .build();
        }
        SdkInvoke sdkInvoke = method.getAnnotation(SdkInvoke.class);
        return SdkContractSnapshot.builder()
                .sdkMethodName(normalizedMethodName)
                .supported(true)
                .needParams(sdkInvoke != null && sdkInvoke.needParams())
                .parameterCount(method.getParameterCount())
                .returnTypeName(method.getReturnType().getName())
                .build();
    }

    /**
     * 查询必然存在的接口信息。
     *
     * @param interfaceInfoId 接口信息 ID
     * @return 接口信息实体
     */
    private InterfaceInfo getRequiredInterfaceInfo(Long interfaceInfoId) {
        validateInterfaceInfoId(interfaceInfoId);
        InterfaceInfo interfaceInfo = interfaceInfoService.getById(interfaceInfoId);
        if (interfaceInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return interfaceInfo;
    }

    /**
     * 校验接口信息 ID。
     *
     * @param interfaceInfoId 接口信息 ID
     */
    private void validateInterfaceInfoId(Long interfaceInfoId) {
        if (interfaceInfoId == null || interfaceInfoId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
    }

    /**
     * 将接口信息实体转换为接口定义快照。
     *
     * @param interfaceInfo 接口信息实体
     * @return 接口定义快照
     */
    private InterfaceDefinitionSnapshot toDefinitionSnapshot(InterfaceInfo interfaceInfo) {
        return InterfaceDefinitionSnapshot.builder()
                .interfaceInfoId(interfaceInfo.getId())
                .name(interfaceInfo.getName())
                .sdkMethodName(interfaceInfo.getSdkMethodName())
                .description(interfaceInfo.getDescription())
                .url(interfaceInfo.getUrl())
                .path(interfaceInfo.getPath())
                .targetHost(interfaceInfo.getTargetHost())
                .requestParams(interfaceInfo.getRequestParams())
                .requestHeader(interfaceInfo.getRequestHeader())
                .responseHeader(interfaceInfo.getResponseHeader())
                .status(interfaceInfo.getStatus())
                .method(interfaceInfo.getMethod())
                .quotaType(interfaceInfo.getQuotaType())
                .userId(interfaceInfo.getUserId())
                .createTime(interfaceInfo.getCreateTime())
                .updateTime(interfaceInfo.getUpdateTime())
                .build();
    }
}
