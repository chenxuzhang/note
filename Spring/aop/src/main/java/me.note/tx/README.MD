
```java 
<!-- 配置事务管理器 -->
<bean id="transactionManager" class="xxx.xxx.DataSourceTransactionManager">
    <property name="dataSource" ref="dataSource"/>
</bean>

第一种
<!-- 配置事务属性 -->
<tx:advice id="advice" transaction-manager="transactionManager">
    <tx:attributes>
        <tx:method name="get*" read-only="true"/>
        <tx:method name="find*" read-only="true"/>
        <tx:method name="*"/>
    </tx:attributes>
</tx:advice>
<!-- 配置事务的切入点： AOP切入 -->
<aop:config>
    <!-- 配置切入表达式 -->
    <aop:pointcut id="pointcut" expression="execution(* com.proc.service.*.*(..))"/>
    <aop:advisor pointcut-ref="pointcut" advice-ref="advice"/>
</aop:config>

第二种
<tx:annotation-driven transaction-manager="transactionManager"/>

第三种
.........
```

```java 
xml配置 对应的 Spring类

<aop:config/> ---> AspectJAwareAdvisorAutoProxyCreator
    xml配置,使用此处理器
    
<aop:advisor/> ---> DefaultBeanFactoryPointcutAdvisor
    advice-ref ---> 自定义Advice接口实现类
    pointcut ---> AspectJExpressionPointcut
    pointcut-ref ---> 自定义Pointcut接口实现类****

<aop:pointcut/> 
    expression ---> AspectJExpressionPointcut
    
<tx:advice> ---> TransactionInterceptor
<tx:attributes> ---> NameMatchTransactionAttributeSource
    接口:TransactionAttributeSource

<tx:annotation-driven>
    aspectj模式
        AnnotationTransactionAspect(org.springframework.transaction.aspectj.*)
        JtaAnnotationTransactionAspect(org.springframework.transaction.aspectj.*)
        
    proxy模式
        InfrastructureAdvisorAutoProxyCreator(扫描Advisor实现类)
        AnnotationTransactionAttributeSource
        TransactionInterceptor
        TransactionAttributeSourcePointcut(依赖TransactionAttributeSource接口)
        BeanFactoryTransactionAttributeSourceAdvisor(advice+pointcut)

一种组合方式
    AspectJAwareAdvisorAutoProxyCreator
    DefaultBeanFactoryPointcutAdvisor
    AspectJExpressionPointcut
    TransactionInterceptor
    NameMatchTransactionAttributeSource

另一种组合方式
    InfrastructureAdvisorAutoProxyCreator
    BeanFactoryTransactionAttributeSourceAdvisor
    TransactionAttributeSourcePointcut
    TransactionInterceptor
    AnnotationTransactionAttributeSource
```