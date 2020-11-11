# 300行代码手写一个SpringMVC

前段时间看了一个视频课，是关于`Spring`源码的，其中就有一个`300`行手写`SpringMVC`的一堂课，我觉得挺棒的，就将大概的内容整理了一下，也就有了这篇文章的诞生；

下面实现的代码，是基于源码实现的思想写的，因此这边文章的重点也是理解源码中所用到的思想，而不是具体的实现代码；

## 基本实现思路

SpringMVC主要的实现部分分为三部分，配置、初始化和运行；

配置即为配置请求映射路径、配置需要加载的类的包路径等，例如`SSM`框架中需要配置`web.xml`文件等（`SpringBoot`实现了`web.xml`文件的自动配置）；

初始化主要是用来扫描类，初始化`Spring`容器、依赖注入等操作；

运行即为当请求到达后台，调用`doGet()`、`doPost()`方法，匹配请求路径，执行对应方法的操作；

下面是一个简化版的`SpringMvc`的实现流程；

![](手写一个springmvc/Xnip2020-11-04_22-33-29.jpg)

## 配置阶段

这里默认已经添加了`Spring`的核心依赖和`Tomcat`依赖，`Servlet`是`tomcat`依赖包中的类，`SpringMVC`的核心功能类`DispatchServlet`需要依赖`Servlet`；

### 配置application.properties文件

这里为了解析方便，采用了`properties`文件来代替`yml`文件，主要配置需要初始化扫描的包路径；

```properties
scanPackage=com.lucas.spring
```

### 配置web.xml文件

在`Spring`中，所有依赖`web`容器的项目，都是从读取`web.xml`文件开始的，因此，这里需要先配置好`web.xml`；

![](手写一个springmvc/image-20201104230125857.png)

下面是`web.xml`中的内容；

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns:javaee="http://java.sun.com/xml/ns/javaee"
         xmlns:web="http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         version="2.5">

    <display-name>Lucas Web Application</display-name>
    <!-- 置servlet，可以配置多个 -->
    <servlet>
        <servlet-name>zmvc</servlet-name>
        <servlet-class>com.lucas.spring.init.v1.ZDispatcherServlet</servlet-class>
        <!-- 初始化参数，可以配置多个 -->
        <init-param>
            <param-name>contextConfigLocation</param-name>
            <param-value>application.properties</param-value>
        </init-param>
        <load-on-startup>1</load-on-startup>
    </servlet>
    <!-- servlet映射，通过servlet-name与上面配置的servlet对应 -->
    <servlet-mapping>
        <servlet-name>zmvc</servlet-name>
        <url-pattern>/*</url-pattern>
    </servlet-mapping>
</web-app>
```

`ZDispatcherServlet`是我们自定义的类，主要是为了模仿实现`DispatchServlet`中的功能；

### 自定义注解Annoation

这里的自定义注解，主要是为了模仿`Spring`中的`@Controller`、`@Service`、`@RequestMapping`、`@RequestParam`、`@Autowired`注解，并模仿实现简化版的注解功能；

#### @ZController注解

```java
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZController {
}
```

#### @ZService注解

```Java
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZService {
    String value() default "";
}
```

#### @ZRequestMapping注解

```Java
@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZRequestMapping {
    String value() default "";
}
```

#### @ZRequestParam注解

```java
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZRequestParam {
    String value() default "";
}
```

#### @ZAutowired注解

```java
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZAutowired {
    String value() default "";
}
```

### 自定义接口层

这里是自定义接口，用来模仿`Spring`中的`Controller`层，并通过上面的自定义注解实现相同的功能；

```java
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
}
```

### 自定义业务层

这里相当于是`Spring`中的`Service`层；

```java
@ZService
public class Service {
    public String get(String name) {
        return "My name is " + name;
    }
}
```

## 初始化阶段

### 初步实现版本

首先，模仿`SpringMVC`的核心功能类`DispatchServlet`，自定义实现`ZDispatchServlet`，同样继承`HttpServlet`（`DispatchServlet`不是直接继承的`HttpServlet`，中间还有夹杂着几层继承关系），重写`HttpServlet`中的`doGet()`，`doPost()`，`init()`方法；

`init()`方法是`SpringMVC`用来初始化的方法，`doGet()`，`doPost()`则是收到请求时会执行的方法；

下面是初步实现的版本，基本展现了`SpringMVC`的初始化思路，但很多代码是写死的，因此还需要优化；

```java
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
import java.util.regex.Pattern;

public class ZDispatcherServlet extends HttpServlet {

    // 保存 application.properties 配置文件中的内容
    private Properties contextConfig = new Properties();

    // 保存扫描的所有的类名
    private List<String> classNames = new ArrayList<String>();

    // IOC容器，为了简化代码，暂时不考虑ConcurrentHashMap
    private Map<String, Object> ioc = new HashMap<String, Object>();

    // 保存 url 和 Method 的对应关系
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

    /**
     * 初始化容器
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
```

### 优化版本

在初步版本中，基本功能以及完全实现，但代码的还不够优雅；例如`HandlerMapping`还不能像`SpringMVC`一样支持正则，`url`参数还不支持强制类型转换，在反射调用前还需要重新获取`beanName`等；

首先，改造`HandlerMapping`，在真实的`Spring`源码中，`HandlerMapping`其实是一个`List`而非`Map`，`List`中的元素是一个自定义的类型；这里首先先自定义一个内部类`Handler`类；

```java
private class Handler{
    // 保存方法对应的Controller实例
    protected Object controller;
    // 保存映射的方法
    protected Method method;
    protected Pattern pattern;
    // 参数顺序
    protected Map<String, Integer> paramIndexMapping;

    public Handler(Pattern pattern, Object controller, Method method) {
        this.controller = controller;
        this.method = method;
        this.pattern = pattern;
        paramIndexMapping = new HashMap<String, Integer>();
        putParamIndexMapping(method);
    }

    private void putParamIndexMapping(Method method) {
        // 提取方法参数上的注解，方法中参数和每个参数上的多个注解组成的二维数组
        Annotation[] [] pa = method.getParameterAnnotations();
        // 遍历参数
        for (int i = 0; i < pa.length ; i ++) {
            // 遍历注解
            for(Annotation a : pa[i]){
                // 判断是否有 @ZRequestParam
                if(a instanceof ZRequestParam){
                    // 拿到 @ZRequestParam 中的参数名，并记录参数顺序
                    String paramName = ((ZRequestParam) a).value();
                    if(!"".equals(paramName.trim())){
                        paramIndexMapping.put(paramName, i);
                    }
                }
            }
        }
        // 获取方法中参数类型
        Class<?> [] paramsTypes = method.getParameterTypes();
        // 遍历参数类型
        for (int i = 0; i < paramsTypes.length ; i ++) {
            Class<?> type = paramsTypes[i];
            
            // 排除 HttpServletRequest和 HttpServletResponse，直接记录参数和位置
            if(type == HttpServletRequest.class || type == HttpServletResponse.class){
                paramIndexMapping.put(type.getName(),i);
            }
        }
    }
}
```

然后再优化`handlerMapping`的结构；将原有的`Map`修改成`Handler`集合；

```java
// 保存 url正则、Controller、Method 的对应关系
private List<Handler> handlerMapping = new ArrayList<Handler>();
```

修改`initHandlerMapping()`方法，通过`Handler`实例保存正则、`Controller`实例和方法实例；满足通过正则来匹配请求路径；

```java
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

        String url = "";
        // 先判断类上是否有请求路径
        if (clazz.isAnnotationPresent(ZRequestMapping.class)) {
            ZRequestMapping requestMapping = clazz.getAnnotation(ZRequestMapping.class);
            url = requestMapping.value();
        }

        // 默认获取所有的 public 方法
        for (Method method : clazz.getMethods()) {
            // 再判断方法上的请求路径，没有注解的方法直接跳过
            if (!method.isAnnotationPresent(ZRequestMapping.class)) {
                continue;
            }

            ZRequestMapping requestMapping = method.getAnnotation(ZRequestMapping.class);
            String regex = ("/" + url + requestMapping.value()).replaceAll("/+", "/");

            Pattern pattern = Pattern.compile(regex);
            // 保存正则，Controller实例，对应的方法实例
            handlerMapping.add(new Handler(pattern, entry.getValue(), method));

            System.out.println("Mapped :" + url + "," + method);
        }
    }
}
```

修改`doDispatch()`方法；实现动态参数类型转换并执行`Controller`中的方法；

```java
private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
    // 根据 HttpServletRequest 请求获取缓存的 Handler
    Handler handler = getHandler(req);
    // 路径不匹配，返回404
    if(handler == null){
        resp.getWriter().write("404 Not Found!");
        return;
    }
    // 获取方法中的参数类型列表
    Class<?> [] paramTypes = handler.method.getParameterTypes();
    // 保存参数值，数组中的参数后面都会根据其类型进行转换，并传入反射执行方法对象的invoke()方法中
    Object [] paramValues = new Object[paramTypes.length];

    // 获取 HttpServletRequest 中携带的参数，key是String型，value是String型数组
    // 例如，request中的参数 t1=1&t1=2&t2=3，即 key=t1, value[0]=1,value[1]=2 ; key=t2, value[0]=3
    Map<String,String[]> params = req.getParameterMap();

    // 遍历请求参数
    for (Map.Entry<String, String[]> param : params.entrySet()) {
        // 将参数值准成 String
        String value = Arrays.toString(param.getValue())
                // url传过的是String型数组, 将数组外的[]给去掉
                .replaceAll("\\[|\\]","")
                // 将空白处替换为逗号 ,
                .replaceAll("\\s",",");
        // 如果不包含这个参数名，说明不需要进行转换，例如 HttpServletRequest
        if(!handler.paramIndexMapping.containsKey(param.getKey())){
            continue;
        }
        // 根据请求参数名获取方法中的参数位置索引
        int index = handler.paramIndexMapping.get(param.getKey());
        // 转换请求参数类型，并赋值给数组对应元素
        paramValues[index] = convert(paramTypes[index], value);
    }

    // 单独判断 HttpServletRequest 和 HttpServletResponse，赋值给参数数组对应元素
    if(handler.paramIndexMapping.containsKey(HttpServletRequest.class.getName())) {
        int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
        paramValues[reqIndex] = req;
    }
    if(handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())) {
        int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
        paramValues[respIndex] = resp;
    }

    // 传入参数数组，执行对应方法
    Object returnValue = handler.method.invoke(handler.controller, paramValues);
    // 返回方法返回值，有值则转成 String, 返回页面
    if(returnValue == null){
        return;
    }
    resp.getWriter().write(returnValue.toString());
}
```

`getHandler()`和`convert()`方法；

```java
/**
 * 根据请求路径获取对应 Handler
 * @param req
 * @return
 * @throws Exception
 */
private Handler getHandler(HttpServletRequest req) throws Exception {
    if(handlerMapping.isEmpty()){
        return null;
    }
    // 这里获取的紧跟端口后的完整请求路径
    String url = req.getRequestURI();
    // 这里获取的是全局配置的请求前缀，和Controller中路径组成完整请求路径
    String contextPath = req.getContextPath();
    // 获取Controller中请求路径
    url = url.replace(contextPath, "").replaceAll("/+", "/");
    for (Handler handler : handlerMapping) {
        try{
            // 根据请求路径正则匹配
            Matcher matcher = handler.pattern.matcher(url);
            // 如果没有匹配上继续下一个匹配
            if(!matcher.matches()){
                continue;
            }
            return handler;
        }catch(Exception e){
            throw e;
        }
    }
    return null;
}

/**
 * 根据参数类型和参数值进行转换
 * url 传过来的参数都是 String 类型，HTTP 是基于字符串协议，只需要把 String 转换为任意类型就好
 * @param type
 * @param value
 * @return
 */
private Object convert(Class<?> type, String value) {
    // 如果是 int
    if (Integer.class == type) {
        return Integer.valueOf(value);
    }
    // 如果是其他类型，那就继续加判断并准换，这里就不再重复实现，可以考虑采用策略模式
    return value;
}
```

此时，一个简易版的`SpringMVC`就完成了，如果启动`Spring`项目，这些使用自定义注解的`Controller`层就可以像`Spring`原生的`Controller`层一样生效了，可以接收到外部发送的`http`请求；

这里运行阶段的效果就不做展示了；

不积硅步，无以至千里，今天的文章你学会了吗？

