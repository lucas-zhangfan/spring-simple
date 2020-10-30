package com.lucas.spring.init;

import com.lucas.spring.annotation.ZAutowired;
import com.lucas.spring.annotation.ZController;
import com.lucas.spring.annotation.ZRequestMapping;
import com.lucas.spring.annotation.ZService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

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

    private Map<String, Object> ioc = new HashMap<String, Object>();
    //保存 url 和 Method 的对应关系
    private Map<String, Method> handlerMapping = new HashMap<String, Method>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //6、调用，运行阶段
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception, Detail : " + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");
        if (!this.handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 Not Found!!");
            return;
        }
        Method method = this.handlerMapping.get(url);
        //第一个参数：方法所在的实例
        //第二个参数：调用时所需要的实参
        Map<String, String[]> params = req.getParameterMap();
        //投机取巧的方式
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName), new Object[]{req, resp, params.get("name")[0]});
        //System.out.println(method);

    }

    //url 传过来的参数都是 String 类型的，HTTP 是基于字符串协议
    //只需要把 String 转换为任意类型就好
    private Object convert(Class<?> type, String value) {
        //如果是 int
        if (Integer.class == type) {
            return Integer.valueOf(value);
        }
        //如果还有 double 或者其他类型，继续加 if
        //这时候，我们应该想到策略模式了
        //在这里暂时不实现，希望小伙伴自己来实现
        return value;
    }

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

        System.out.println("GP Spring framework is init.");
    }

    /**
     * 初始化 HandlerMapping
     */
    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(ZController.class)) {
                continue;
            }
            //保存写在类上面的@GPRequestMapping("/demo")
            String baseUrl = "";
            if (clazz.isAnnotationPresent(ZRequestMapping.class)) {
                ZRequestMapping requestMapping = clazz.getAnnotation(ZRequestMapping.class);
                baseUrl = requestMapping.value();
            }
            //默认获取所有的 public 方法
            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(ZRequestMapping.class)) {
                    continue;
                }
                ZRequestMapping requestMapping = method.getAnnotation(ZRequestMapping.class);//优化
                // //demo///query
                String url = ("/" + baseUrl + "/" + requestMapping.value())
                        .replaceAll("/+", "/");
                handlerMapping.put(url, method);
                System.out.println("Mapped :" + url + "," + method);
            }
        }
    }

    private void doAutowired() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //Declared 所有的，特定的 字段，包括 private/protected/default
            //正常来说，普通的 OOP 编程只能拿到 public 的属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(ZAutowired.class)) {
                    continue;
                }
                ZAutowired autowired = field.getAnnotation(ZAutowired.class);
                //如果用户没有自定义 beanName，默认就根据类型注入
                //这个地方省去了对类名首字母小写的情况的判断，这个作为课后作业
                //小伙伴们自己去完善
                String beanName = autowired.value().trim();
                if ("".equals(beanName)) {
                    //获得接口的类型，作为 key 待会拿这个 key 到 ioc 容器中去取值
                    beanName = field.getType().getName();
                }
                //如果是 public 以外的修饰符，只要加了@Autowired 注解，都要强制赋值
                //反射中叫做暴力访问， 强吻
                field.setAccessible(true);
                try {
                    //用反射机制，动态给字段赋值
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
                    // 1、自定义的 beanName
                    ZService service = clazz.getAnnotation(ZService.class);
                    String beanName = service.value();

                    // 2、默认类名首字母小写
                    if ("".equals(beanName.trim())) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();

                    // 3、以自定义名称，将bean加入到ioc容器
                    ioc.put(beanName, instance);

                    // 4、TODO 根据类型自动赋值,投机取巧的方式
                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i.getName())) {
                            throw new Exception("The “" + i.getName() + "” is exists!!");
                        }
                        //把接口的类型直接当成 key 了
                        ioc.put(i.getName(), instance);
                    }
                } else {
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
}
