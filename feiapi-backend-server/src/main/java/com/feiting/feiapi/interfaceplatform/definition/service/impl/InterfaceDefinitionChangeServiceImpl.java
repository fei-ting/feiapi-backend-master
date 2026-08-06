package com.feiting.feiapi.interfaceplatform.definition.service.impl;

import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.InterfaceDefinitionSnapshot;
import com.feiting.feiapi.interfaceplatform.definition.service.api.InterfaceDefinitionChangeService;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * 接口定义变更判断服务实现。
 */
@Service
public class InterfaceDefinitionChangeServiceImpl implements InterfaceDefinitionChangeService {

    /**
     * 判断请求方法或运行时请求参数模板是否发生变化。
     *
     * @param oldDefinition    更新前接口定义快照
     * @param latestDefinition 更新后接口定义快照
     * @return 请求文档模板是否变化
     */
    @Override
    public boolean requestDocTemplateChanged(InterfaceDefinitionSnapshot oldDefinition,
                                             InterfaceDefinitionSnapshot latestDefinition) {
        return !Objects.equals(oldDefinition.getRequestParams(), latestDefinition.getRequestParams())
                || !Objects.equals(oldDefinition.getMethod(), latestDefinition.getMethod());
    }

    /**
     * 判断受控接口配置是否发生有效变化。
     *
     * @param oldDefinition    更新前接口定义快照
     * @param latestDefinition 更新后接口定义快照
     * @return 是否发生有效变化
     */
    @Override
    public boolean controlledConfigChanged(InterfaceDefinitionSnapshot oldDefinition,
                                           InterfaceDefinitionSnapshot latestDefinition) {
        return Stream.of(
                        !Objects.equals(oldDefinition.getName(), latestDefinition.getName()),
                        !Objects.equals(oldDefinition.getDescription(), latestDefinition.getDescription()),
                        !Objects.equals(oldDefinition.getMethod(), latestDefinition.getMethod()),
                        !Objects.equals(oldDefinition.getPath(), latestDefinition.getPath()),
                        !Objects.equals(oldDefinition.getTargetHost(), latestDefinition.getTargetHost()),
                        !Objects.equals(oldDefinition.getUrl(), latestDefinition.getUrl()),
                        !Objects.equals(oldDefinition.getQuotaType(), latestDefinition.getQuotaType()),
                        !Objects.equals(oldDefinition.getSdkMethodName(), latestDefinition.getSdkMethodName()),
                        !Objects.equals(oldDefinition.getRequestParams(), latestDefinition.getRequestParams()))
                .anyMatch(Boolean.TRUE::equals);
    }
}
