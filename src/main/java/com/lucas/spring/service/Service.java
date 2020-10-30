package com.lucas.spring.service;

import com.lucas.spring.annotation.ZService;

/**
 * @author zhangfan
 * @description
 * @date 2020/10/30 16:46
 **/
@ZService
public class Service {
    public String get(String name) {
        return "My name is " + name;
    }
}
