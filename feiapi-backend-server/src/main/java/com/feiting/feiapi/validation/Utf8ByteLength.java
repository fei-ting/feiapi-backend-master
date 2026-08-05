package com.feiting.feiapi.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按 UTF-8 实际字节数校验文本长度。
 */
@Documented
@Constraint(validatedBy = Utf8ByteLengthValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Utf8ByteLength {

    /**
     * 校验失败消息。
     *
     * @return 校验失败消息
     */
    String message() default "文本 UTF-8 字节数超过限制";

    /**
     * 允许的最大 UTF-8 字节数。
     *
     * @return 最大字节数
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
