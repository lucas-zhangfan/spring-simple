package com.lucas.spring.init.v1;

import com.lucas.spring.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author zhangfan
 * @description
 * @date 2020/10/30 15:10
 **/
public class ZDispatcherServlet extends HttpServlet {

    //保存 application.properties 配置文件中的内容
    private Properties contextConfig = new Properties();

    //保存扫描的所有的类名
    private List<String> classNames = new ArrayList<String>();

    // IOC容器，为了简化代码，暂时不考虑ConcurrentHashMap
    private Map<String, Object> ioc = new HashMap<String, Object>();

    //保存 url 和 Method 的对应关系
    private Map<String, Method> handlerMapping = new HashMap<String, Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 6、初始化完成后，接口收到请求后，调用，运行阶段
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception, Detail : " + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        // 获取请求的ip:端口后的全部路径，即上下文和@RequestMapping拼接的全路径
        String url = req.getRequestURI();
        // 从请求中获取全局配置的请求上下文，如 /api
        String contextPath = req.getContextPath();
        // @RequestMapping中的路径是去除上下文后的路径
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");
        // 未匹配到对应的路径，直接404
        if (!this.handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!!");
            return;
        }

        // 反射执行路径所对应的Controller中的方法
        Method method = this.handlerMapping.get(url);
        // 获取到请求携带的参数名以及对应的参数值
        Map<String, String[]> params  = req.getParameterMap();

        Class<?>[] parameterTypes = method.getParameterTypes();

        Object[] paramValues = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];

            // 不能用 instanceof，parameterType不是实参，而是形参
            if(parameterType == HttpServletRequest.class){
                paramValues[i] = req;

            }else if(parameterType == HttpServletResponse.class){
                paramValues[i] = resp;

            }else if(parameterType == String.class){
                ZRequestParam requestParam = parameterType.getAnnotation(ZRequestParam.class);
                if(params.containsKey(requestParam.value())) {
                    for (Map.Entry<String,String[]> param : params.entrySet()){
                        String value = Arrays.toString(param.getValue())
                                .replaceAll("\\[|\\]","")
                                .replaceAll("\\s",",");
                        paramValues[i] = value;
                    }
                }
            }
        }

        // 这里获取方法所在的类的名字，用于从IOC中拿到对应的类的实例
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        // 执行方法，传入方法对参数，这里是写死的，后续优化
        method.invoke(ioc.get(beanName), paramValues);

    }
//
//    //url 传过来的参数都是 String 类型的，HTTP 是基于字符串协议
//    //只需要把 String 转换为任意类型就好
//    private Object convert(Class<?> type, String value) {
//        //如果是 int
//        if (Integer.class == type) {
//            return Integer.valueOf(value);
//        }
//        //如果还有 double 或者其他类型，继续加 if
//        //这时候，我们应该想到策略模式了
//        //在这里暂时不实现，希望小伙伴自己来实现
//        return value;
//    }

    /**
     * 初始化容器
     *
     * @param config
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {

        // 1、加载配置文件 Servlet读取web.xml文件, 参数 contextConfigLocation 对应值为 application.properties
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        // 2、扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        // 3、初始化扫描到的类，并且将它们放入到 ICO 容器之中
        doInstance();
        // 4、完成依赖注入
        doAutowired();
        // 5、初始化 HandlerMapping
        initHandlerMapping();

        System.out.println("Z Spring framework is init.");
    }

    /**
     * 初始化 HandlerMapping
     */
    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {

            // 主要处理Controller类
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(ZController.class)) {
                continue;
            }

            String baseUrl = "";
            // 先判断类上是否有请求路径
            if (clazz.isAnnotationPresent(ZRequestMapping.class)) {
                ZRequestMapping requestMapping = clazz.getAnnotation(ZRequestMapping.class);
                baseUrl = requestMapping.value();
            }


            // 默认获取所有的 public 方法
            for (Method method : clazz.getMethods()) {

                // 再判断方法上的请求路径，没有注解的方法直接跳过
                if (!method.isAnnotationPresent(ZRequestMapping.class)) {
                    continue;
                }

                ZRequestMapping requestMapping = method.getAnnotation(ZRequestMapping.class);
                // 先拼接，再把有多个 / 的位置转成只有一个
                String url = ("/" + baseUrl + "/" + requestMapping.value())
                        .replaceAll("/+", "/");

                // 保存路径和方法
                handlerMapping.put(url, method);

                System.out.println("Mapped :" + url + "," + method);
            }
        }
    }

    /**
     * 完成依赖注入
     */
    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        // 遍历容器中所有bean
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            // 获取所有的字段 public/private/protected/default 的属性字段
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                // 只对有注解的属性进行注入
                if (!field.isAnnotationPresent(ZAutowired.class)) {
                    continue;
                }
                ZAutowired autowired = field.getAnnotation(ZAutowired.class);

                // 如果没有指定自定义beanName，默认就根据类型注入, (这里忽略了对类名首字母小写的情况的判断)
                String beanName = autowired.value().trim();
                if ("".equals(beanName)) {
                    // 获得接口的类型，根据这个key到ioc容器中取值
                    beanName = field.getType().getName();
                }

                // 如果是 public 以外的修饰符，只要加了注解，都要强制赋值
                field.setAccessible(true);
                try {
                    // 给字段赋值
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 初始化扫描到的类，并且将它们放入到 ICO 容器之中
     */
    private void doInstance() {
        // 初始化，为依赖注入DI做准备
        if (classNames.isEmpty()) {
            return;
        }
        try {
            for (String className : classNames) {
                // 生成字节码文件，为类的初始化做准备
                Class<?> clazz = Class.forName(className);

                // 只要加了注解的类，才需要初始化，这里只列举 @Controller 和 @Service 两个注解
                // spring中还有 @Component、@Configuration 等
                if (clazz.isAnnotationPresent(ZController.class)) {
                    Object instance = clazz.newInstance();
                    // 1、Spring 默认类名首字母小写
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    // 2、将bean加入到ioc容器
                    ioc.put(beanName, instance);

                } else if (clazz.isAnnotationPresent(ZService.class)) {
                    // 1、判断是否有自定义的 beanName
                    ZService service = clazz.getAnnotation(ZService.class);
                    String beanName = service.value();

                    // 2、默认类名首字母小写
                    if ("".equals(beanName.trim())) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();

                    // 3、以自定义名称，将bean加入到ioc容器
                    ioc.put(beanName, instance);

                    // 4、根据类型自动赋值，将类的接口的全类名也作为key，方便使用类型获取对象（这里是通过类型获取的简化写法）
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i.getName())) {
                            // 接口类型重复，不重复保存
                            throw new Exception("The “" + i.getName() + "” is exists!!");
                        }
                        //把接口的类型直接当成 key 了
                        ioc.put(i.getName(), instance);
                    }
                } else {
                    // 这里是表示，例如@Component等注解的逻辑，略过
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 将首字母变小写
     *
     * @param simpleName
     * @return
     */
    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        // 大小写字母的 ASCII 码相差 32，大写字母的 ASCII 码要小于小写字母的 ASCII 码
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     * 扫描相关的类
     *
     * @param scanPackage
     */
    private void doScanner(String scanPackage) {
        // scanPackage=com.lucas.spring 存储的是包路径 , 转换为文件路径，把 . 替换为 /
        // 路径为 /com/lucas/spring
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));

        if (url != null) {
            File classPath = new File(url.getFile());
            for (File file : classPath.listFiles()) {
                if (file.isDirectory()) {
                    // 是文件夹就继续向下扫描
                    doScanner(scanPackage + "." + file.getName());
                } else {
                    // 不处理不是.class结尾的文件
                    if (!file.getName().endsWith(".class")) {
                        continue;
                    }
                    // 去掉文件名后的.class, 保存全路径到缓存中
                    String className = (scanPackage + "." + file.getName().replace(".class", ""));
                    classNames.add(className);
                }
            }

        } else {
            throw new RuntimeException("包路径不对");
        }
    }

    /**
     * 加载配置文件
     *
     * @param contextConfigLocation
     */
    private void doLoadConfig(String contextConfigLocation) {

        // contextConfigLocation = application.properties
        InputStream fis = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);

        try {
            // 保存application.properties中键值对
            contextConfig.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class Handler{
        // 保存方法对应的Controller实例
        protected Object controller;
        // 保存映射的方法
        protected Method method;
        protected Pattern pattern;
        // 参数顺序
        protected Map<String, Integer> paramIndexMapping;

        public Handler(Object controller, Method method, Pattern pattern) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            //提取方法中加了注解的参数
            Annotation[] [] pa = method.getParameterAnnotations();
            for (int i = 0; i < pa.length ; i ++) {
                for(Annotation a : pa[i]){
                    if(a instanceof ZRequestParam){
                        String paramName = ((ZRequestParam) a).value();
                        if(!"".equals(paramName.trim())){
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }
            //提取方法中的 request 和 response 参数
            Class<?> [] paramsTypes = method.getParameterTypes();
            for (int i = 0; i < paramsTypes.length ; i ++) {Class<?> type = paramsTypes[i];
                if(type == HttpServletRequest.class ||
                        type == HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(),i);
                }
            }
        }
    }
}
