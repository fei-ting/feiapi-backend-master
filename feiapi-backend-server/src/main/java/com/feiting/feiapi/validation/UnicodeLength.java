package com.feiting.feiapi.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按去除首尾 Unicode 空白后的码点数量校验文本长度。
 */
@Documented
@Constraint(validatedBy = UnicodeLengthValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface UnicodeLength {

    /**
     * 校验失败消息。
     *
     * @return 校验失败消息
     */
    String message() default "文本长度超过限制";

    /**
     * 允许的最大 Unicode 码点数量。
     *
     * @return 最大码点数量
     */
    int max();

    /**
     * Jakarta Validation 分组。
     *
     * @return 校验分组
     */
    Class<?>[] groups() default {};

    /**
     * Jakarta Validation 负载。
     *
     * @return 校验负载
     */
    Class<? extends Payload>[] payload() default {};
}
