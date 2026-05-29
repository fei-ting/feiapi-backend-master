package com.feiting.feiapiclientsdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记允许被平台注册并调用的 SDK 方法。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SdkInvoke {

    /**
     * 当前方法是否需要请求参数。
     */
    boolean needParams() default false;
}
