package com.lucas.spring.annotation;

import java.lang.annotation.*;

/**
 * @author zhangfan
 * @description
 * @date 2020/10/30 15:36
 **/

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZAutowired {
    String value() default "";
}
