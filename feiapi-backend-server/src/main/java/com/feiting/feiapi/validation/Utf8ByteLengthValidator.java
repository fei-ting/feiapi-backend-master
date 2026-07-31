package com.feiting.feiapi.validation;

import com.feiting.feiapi.utils.TextSizeUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * UTF-8 字节长度约束校验器。
 */
public class Utf8ByteLengthValidator implements ConstraintValidator<Utf8ByteLength, String> {

    /** 允许的最大 UTF-8 字节数。 */
    private int max;

    /**
     * 初始化约束参数。
     *
     * @param constraintAnnotation UTF-8 字节长度约束
     */
    @Override
    public void initialize(Utf8ByteLength constraintAnnotation) {
        this.max = constraintAnnotation.max();
    }

    /**
     * 校验文本是否未超过 UTF-8 字节上限。
     *
     * @param value   待校验文本
     * @param context 校验上下文
     * @return 文本为空或字节数合法时返回 true
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || TextSizeUtils.utf8ByteLength(value) <= max;
    }
}
