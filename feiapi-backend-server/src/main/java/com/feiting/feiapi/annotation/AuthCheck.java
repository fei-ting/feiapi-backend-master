package com.feiting.feiapi.annotation;

import com.feiting.feiapi.model.enums.UserRoleEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验
 *
 * @author yupi
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {


    /**
     * 有任何一个角色
     *
     * @return
     */
    UserRoleEnum[] anyRole() default {};

    /**
     * 必须有某个角色
     *
     * @return
     */
    UserRoleEnum mustRole() default UserRoleEnum.NONE;

}


