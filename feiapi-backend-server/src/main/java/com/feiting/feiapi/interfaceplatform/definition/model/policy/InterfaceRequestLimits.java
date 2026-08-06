package com.feiting.feiapi.interfaceplatform.definition.model.policy;

/**
 * 接口请求定义限制策略。
 *
 * <p>该类只公开运行时请求定义需要的稳定限制值，不包含持久化能力或业务编排逻辑。</p>
 */
public final class InterfaceRequestLimits {

    /**
     * 运行时请求参数数量上限。
     */
    public static final int MAX_REQUEST_PARAM_COUNT = 100;

    /**
     * 运行时参数名称最大 Unicode 码点数量。
     */
    public static final int MAX_PARAM_NAME_LENGTH = 128;

    /**
     * 运行时参数示例值最大 Unicode 码点数量。
     */
    public static final int MAX_PARAM_EXAMPLE_VALUE_LENGTH = 1024;

    /**
     * 运行时请求参数模板最大 UTF-8 字节数。
     */
    public static final int MAX_RUNTIME_REQUEST_BODY_BYTES = 65535;

    /**
     * 隐藏工具类构造方法。
     */
    private InterfaceRequestLimits() {
    }
}
