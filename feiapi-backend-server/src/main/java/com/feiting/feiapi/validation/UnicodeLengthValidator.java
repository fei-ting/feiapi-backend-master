package com.feiting.feiapi.validation;

import com.feiting.feiapi.utils.TextSizeUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Unicode 码点长度约束校验器。
 */
public class UnicodeLengthValidator implements ConstraintValidator<UnicodeLength, String> {

    /** 允许的最大 Unicode 码点数量。 */
    private int max;

    /**
     * 初始化约束参数。
     *
     * @param constraintAnnotation Unicode 长度约束
     */
    @Override
    public void initialize(UnicodeLength constraintAnnotation) {
        this.max = constraintAnnotation.max();
    }

    /**
     * 校验文本是否未超过 Unicode 码点上限。
     *
     * @param value   待校验文本
     * @param context 校验上下文
     * @return 文本为空或长度合法时返回 true
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || TextSizeUtils.unicodeLengthAfterStrip(value) <= max;
    }
}
