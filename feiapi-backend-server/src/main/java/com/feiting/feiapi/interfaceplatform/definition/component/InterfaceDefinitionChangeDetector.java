package com.feiting.feiapi.interfaceplatform.definition.component;

import com.feiting.feiapicommon.model.entity.InterfaceInfo;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * 接口定义变更检测器。
 *
 * <p>用于集中判断接口运行定义是否发生影响文档同步或文档状态的有效变化。</p>
 */
@Component
public class InterfaceDefinitionChangeDetector {

    /**
     * 判断请求方法或运行时请求参数模板是否发生变化。
     *
     * @param oldInterfaceInfo    更新前接口信息
     * @param latestInterfaceInfo 更新后的数据库最终值
     * @return 请求文档模板是否变化
     */
    public boolean requestDocTemplateChanged(InterfaceInfo oldInterfaceInfo, InterfaceInfo latestInterfaceInfo) {
        return !Objects.equals(oldInterfaceInfo.getRequestParams(), latestInterfaceInfo.getRequestParams())
                || !Objects.equals(oldInterfaceInfo.getMethod(), latestInterfaceInfo.getMethod());
    }

    /**
     * 判断管理员维护的受控接口配置是否发生有效变化。
     *
     * <p>方法和请求参数与模板变化判断有意重叠，此处只负责决定是否将已维护文档降为草稿。</p>
     *
     * @param oldInterfaceInfo    更新前接口信息
     * @param latestInterfaceInfo 更新后的数据库最终值
     * @return 是否发生有效变化
     */
    public boolean controlledConfigChanged(InterfaceInfo oldInterfaceInfo, InterfaceInfo latestInterfaceInfo) {
        return Stream.of(
                        !Objects.equals(oldInterfaceInfo.getName(), latestInterfaceInfo.getName()),
                        !Objects.equals(oldInterfaceInfo.getDescription(), latestInterfaceInfo.getDescription()),
                        !Objects.equals(oldInterfaceInfo.getMethod(), latestInterfaceInfo.getMethod()),
                        !Objects.equals(oldInterfaceInfo.getPath(), latestInterfaceInfo.getPath()),
                        !Objects.equals(oldInterfaceInfo.getTargetHost(), latestInterfaceInfo.getTargetHost()),
                        !Objects.equals(oldInterfaceInfo.getUrl(), latestInterfaceInfo.getUrl()),
                        !Objects.equals(oldInterfaceInfo.getQuotaType(), latestInterfaceInfo.getQuotaType()),
                        !Objects.equals(oldInterfaceInfo.getSdkMethodName(), latestInterfaceInfo.getSdkMethodName()),
                        !Objects.equals(oldInterfaceInfo.getRequestParams(), latestInterfaceInfo.getRequestParams()))
                .anyMatch(Boolean.TRUE::equals);
    }
}
