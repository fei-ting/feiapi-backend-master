package com.feiting.feiapiclientsdk.model;

/**
 * SDK 方法发布探测安全策略。
 */
public enum ProbeStrategy {

    /**
     * 未声明策略，仅作为注解默认值，发布检查必须拒绝。
     */
    UNSPECIFIED,

    /**
     * 使用模拟数据执行真实无副作用调用。
     */
    SAFE_REAL_CALL,

    /**
     * 下游提供专用无副作用探测逻辑。
     */
    DEDICATED_PROBE
}
