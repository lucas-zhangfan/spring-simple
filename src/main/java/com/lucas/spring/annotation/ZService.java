package com.lucas.spring.annotation;

import java.lang.annotation.*;

/**
 * @author zhangfan
 * @description
 * @date 2020/10/30 15:35
 **/

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZService {
    String value() default "";
}
