package com.feiting.feiapi.interfaceplatform.definition.service.api;

import com.feiting.feiapi.interfaceplatform.definition.model.snapshot.InterfaceDefinitionSnapshot;

/**
 * 接口定义变更判断服务。
 *
 * <p>用于协调层判断接口定义变化是否需要同步请求文档或降级文档状态。</p>
 */
public interface InterfaceDefinitionChangeService {

    /**
     * 判断请求方法或运行时请求参数模板是否发生变化。
     *
     * @param oldDefinition    更新前接口定义快照
     * @param latestDefinition 更新后接口定义快照
     * @return 请求文档模板是否变化
     */
    boolean requestDocTemplateChanged(InterfaceDefinitionSnapshot oldDefinition,
                                      InterfaceDefinitionSnapshot latestDefinition);

    /**
     * 判断受控接口配置是否发生有效变化。
     *
     * @param oldDefinition    更新前接口定义快照
     * @param latestDefinition 更新后接口定义快照
     * @return 是否发生有效变化
     */
    boolean controlledConfigChanged(InterfaceDefinitionSnapshot oldDefinition,
                                    InterfaceDefinitionSnapshot latestDefinition);
}
