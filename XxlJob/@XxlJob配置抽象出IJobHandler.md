##### 执行器配置 和 @XxlJob的使用

```java
Spring项目作为执行器时的配置(也支持非Spring配置)

@Bean
public XxlJobSpringExecutor xxlJobExecutor() {
 XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
 xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
 xxlJobSpringExecutor.setAppname(appname);
 xxlJobSpringExecutor.setAddress(address);
 xxlJobSpringExecutor.setIp(ip);
 xxlJobSpringExecutor.setPort(port);
 xxlJobSpringExecutor.setAccessToken(accessToken);
 xxlJobSpringExecutor.setLogPath(logPath);
 xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);
 return xxlJobSpringExecutor;
}
------------------------------------------------------------------------
@XxlJob的使用

@Component
public class SampleXxlJob {
 @XxlJob(value = "demoJobHandler", init = "初始化方法名,并不是只执行一次", destroy = "销毁方法,和init是成双成对的")
 public void demoJobHandler() throws Exception {
  // 调度业务
 }
}
```



##### XxlJobSpringExecutor 加载 @XxlJob 注解标记的方法

```java
public class XxlJobSpringExecutor extends XxlJobExecutor implements   
  ApplicationContextAware,SmartInitializingSingleton, DisposableBean {
 // ApplicationContextAware 可自动感知到Spring容器
 // SmartInitializingSingleton 单例bean初始化之后,触发的回调方法,在此之前 bean 已初始化,属性已注入完毕,后置处理器已处理完毕
 // DisposableBean bean 在Spring容器中销毁的时候,会触发回调方法
 @Override
 public void afterSingletonsInstantiated() {
    // 处理 @XxlJob 标记的方法
    initJobHandlerMethodRepository(applicationContext);

    // refresh GlueFactory
    GlueFactory.refreshInstance(1);

   // super start
   try {
    super.start();
   } catch (Exception e) {
    throw new RuntimeException(e);
   }
 }
 
 // @XxlJob 标记的方法存储到内存中
 private void initJobHandlerMethodRepository(ApplicationContext applicationContext) {
  if (applicationContext == null) {
   return;
  }
  // init job handler from method
  String[] beanDefinitionNames = applicationContext.getBeanNamesForType(Object.class, false, true);
  for (String beanDefinitionName : beanDefinitionNames) {
   Object bean = applicationContext.getBean(beanDefinitionName);

   Map<Method, XxlJob> annotatedMethods = null;   // referred to ：org.springframework.context.event.EventListenerMethodProcessor.processBean
   try {
    // 从Spring容器中获取 @XxlJob 注解标记的方法
    annotatedMethods = MethodIntrospector.selectMethods(bean.getClass(),
                        new MethodIntrospector.MetadataLookup<XxlJob>() {
                            @Override
                            public XxlJob inspect(Method method) {
                                return AnnotatedElementUtils.findMergedAnnotation(method, XxlJob.class);
                            }
                        });
   } catch (Throwable ex) {
    logger.error("xxl-job method-jobhandler resolve error for bean[" + beanDefinitionName + "].", ex);
   }

   if (annotatedMethods==null || annotatedMethods.isEmpty()) {
    continue;
   }
   // 通过父类 XxlJobExecutor.registJobHandler(...) 进行注册
   for (Map.Entry<Method, XxlJob> methodXxlJobEntry : annotatedMethods.entrySet()) {
    Method executeMethod = methodXxlJobEntry.getKey();
    XxlJob xxlJob = methodXxlJobEntry.getValue();
    registJobHandler(xxlJob, bean, executeMethod);
   }
  }
 }
}
```



##### XxlJobExecutor.registJobHandler(...) 注册逻辑

```java
// @XxlJob注解配置在内存中存储. key:@XxlJob配置的value值,value:@XxlJob映射出的IJobHandler
private static ConcurrentMap<String, IJobHandler> jobHandlerRepository = new ConcurrentHashMap<String, IJobHandler>();

protected void registJobHandler(XxlJob xxlJob, Object bean, Method executeMethod){
 if (xxlJob == null) {
  return;
 }

 String name = xxlJob.value();
 //make and simplify the variables since they'll be called several times later
 Class<?> clazz = bean.getClass();
 String methodName = executeMethod.getName();
 if (name.trim().length() == 0) {
  throw new RuntimeException("xxl-job method-jobhandler name invalid, for[" + clazz + "#" + methodName + "] .");
 }
 if (loadJobHandler(name) != null) {
  throw new RuntimeException("xxl-job jobhandler[" + name + "] naming conflicts.");
 }

 executeMethod.setAccessible(true);

 // init and destroy
 Method initMethod = null;
 Method destroyMethod = null;
 // @XxlJob 配置的init值,从类中寻找初始化方法(该方法有可能会执行多次,执行多次原因参考 "JobThread.md")
 if (xxlJob.init().trim().length() > 0) {
   try {
    initMethod = clazz.getDeclaredMethod(xxlJob.init());
    initMethod.setAccessible(true);
  } catch (NoSuchMethodException e) {
    throw new RuntimeException("xxl-job method-jobhandler initMethod invalid, for[" + clazz + "#" + methodName + "] .");
  }
 }
 // @XxlJob 配置的destroy值,从类中寻找销毁方法(该方法有可能会执行多次,执行多次原因参考 "JobThread.md")
 if (xxlJob.destroy().trim().length() > 0) {
  try {
   destroyMethod = clazz.getDeclaredMethod(xxlJob.destroy());
   destroyMethod.setAccessible(true);
  } catch (NoSuchMethodException e) {
   throw new RuntimeException("xxl-job method-jobhandler destroyMethod invalid, for[" + clazz + "#" + methodName + "] .");
  }
 }

 // registry jobhandler. MethodJobHandler 继承了IJobHandler,持有方法和方法所属的实例对象,基于反射调用
 registJobHandler(name, new MethodJobHandler(bean, executeMethod, initMethod, destroyMethod));
}

public static IJobHandler registJobHandler(String name, IJobHandler jobHandler){
 return jobHandlerRepository.put(name, jobHandler);
}
```

