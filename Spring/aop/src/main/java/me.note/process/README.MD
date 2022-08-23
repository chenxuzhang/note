###原生Spring Aop 初始化流程、执行流程

### 初始化流程


### 执行流程

```java 
第一种
<aop:config>
    <aop:advisor advice-ref="xxx" pointcut="execution(* com.spring_aop.Human.*(..))" order="1"/>
</aop:config>

第二种
<aop:config>
    <aop:advisor advice-ref="xxx" pointcut-ref="xxxx" order="1"/>
</aop:config>

第三种
<aop:config proxy-target-class="true">
    <aop:pointcut id="abc" expression="execution(* com.spring_aop.Human.*(..))"/>
    <aop:advisor advice-ref="xxx" pointcut-ref="abc" order="1"/>
</aop:config>

第四种
<aop:config>
    <aop:aspect id="TestAspect" ref="xmlAspect">
        <aop:pointcut id="businessService" expression="execution(* aop.*.*(..))"/>
        <aop:around pointcut-ref="businessService" method="doAround"/>
    </aop:aspect> 
</aop:config>
```

```java 
xml配置 对应的 Spring类

<aop:aspectj-autoproxy/> ---> AnnotationAwareAspectJAutoProxyCreator
    基于Aspectj注解的后置处理器,用于搜索适合某些bean使用的Advisor(Advice+Pointcut)

<aop:config/> ---> AspectJAwareAdvisorAutoProxyCreator
    xml配置,使用此处理器

InfrastructureAdvisorAutoProxyCreator
    不使用Aspectj,会初始化此处理器。
    例:spring xml 基于注解的事务,如果不是aspectj模式,则使用此模式(proxy,默认)

<aop:advisor/> ---> DefaultBeanFactoryPointcutAdvisor
    advice-ref ---> 自定义Advice接口实现类
    pointcut ---> AspectJExpressionPointcut
    pointcut-ref ---> 自定义Pointcut接口实现类

<aop:pointcut/> 
    expression ---> AspectJExpressionPointcut

<aop:aspect/> 
    对应多个advisor(advice + pointcut(切点匹配连接点))
    取决于有几个aop:before、aop:after、aop:after-returning、aop:after-throwing、aop:around

    declare-parents ---> DeclareParentsAdvisor
    aop:before ---> AspectJMethodBeforeAdvice(AbstractAspectJAdvice)
    aop:after ---> AspectJAfterAdvice(AbstractAspectJAdvice)
    aop:after-returning ---> AspectJAfterReturningAdvice(AbstractAspectJAdvice)
    aop:after-throwing ---> AspectJAfterThrowingAdvice(AbstractAspectJAdvice)
    aop:around ---> AspectJAroundAdvice
    aop:pointcut ---> 参考上面
    
    // aop:before、aop:after、aop:after-returning、aop:after-throwing、aop:around 每个advice都对应一个advisor,即AspectJPointcutAdvisor
    advisor ---> AspectJPointcutAdvisor
    
```