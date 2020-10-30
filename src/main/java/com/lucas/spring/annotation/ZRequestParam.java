package com.lucas.spring.annotation;

import java.lang.annotation.*;

/**
 * @author zhangfan
 * @description
 * @date 2020/10/30 15:37
 **/
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZRequestParam {
    String value() default "";
}
