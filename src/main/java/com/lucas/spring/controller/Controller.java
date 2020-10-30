package com.lucas.spring.controller;

import com.lucas.spring.annotation.ZAutowired;
import com.lucas.spring.annotation.ZController;
import com.lucas.spring.annotation.ZRequestMapping;
import com.lucas.spring.annotation.ZRequestParam;
import com.lucas.spring.service.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author zhangfan
 * @description
 * @date 2020/10/30 16:45
 **/
@ZController
@ZRequestMapping("web")
public class Controller {

    @ZAutowired
    private Service service;
    @ZRequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp,
                      @ZRequestParam("name") String name){
        String result = service.get(name);
        try {
            resp.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @ZRequestMapping("/add")
    public void add(HttpServletRequest req, HttpServletResponse resp,
                    @ZRequestParam("a") Integer a, @ZRequestParam("b") Integer b){
        try {
            resp.getWriter().write(a + "+" + b + "=" + (a + b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @ZRequestMapping("/remove")
    public void remove(HttpServletRequest req,HttpServletResponse resp,
                       @ZRequestParam("id") Integer id){
    }
}
