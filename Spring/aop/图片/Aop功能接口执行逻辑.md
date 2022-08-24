```
接口执行逻辑只需要我们根据aop定义的接口进行功能的实现即可
1、Advice接口
2、Pointcut接口(含ClassFilter和MethodMatcher),spring也已提供了部分接口实现
    例如:
        NameMatchMethodPointcut 根据名称进行匹配
        JdkRegexpMethodPointcut 表达式进行匹配
        DynamicMethodMatcherPointcut、StaticMethodMatcherPointcut
        TransactionAttributeSourcePointcut 事务有关的
        AspectJExpressionPointcut AspectJ有关的
        ComposablePointcut 组合Pointcut,多个Pointcut且或者或的关系会用到
        TruePointcut、GetterPointcut
3、PointcutAdvisor接口(Advisor的子接口),目前此接口已有默认实现
    例如:
        DefaultPointcutAdvisor 默认的,可自定义Pointcuth(默认是true)和Advice
        AspectJPointcutAdvisor AspectJ有关的
        AspectJExpressionPointcutAdvisor AspectJ有关的
        InstantiationModelAwarePointcutAdvisorImpl AspectJ有关的
        RegexpMethodPointcutAdvisor 正则表达式相关的(内部持有JdkRegexpMethodPointcut)
        TransactionAttributeSourceAdvisor 事务相关的PointcutAdvisor
```

![Aop功能接口执行逻辑](https://github.com/chenxuzhang/note/blob/main/Spring/aop/%E5%9B%BE%E7%89%87/Aop%E5%8A%9F%E8%83%BD%E6%8E%A5%E5%8F%A3%E6%89%A7%E8%A1%8C%E9%80%BB%E8%BE%91.jpg)