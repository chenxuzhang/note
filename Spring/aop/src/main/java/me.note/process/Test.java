package me.note.process;

import me.note.process.init.*;
import me.note.process.exe.TestDefaultAdvised;
import me.note.process.exe.TestDefaultAdvisorChainFactory;
import me.note.process.exe.TestDefaultMethodInvocation;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.*;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.AdvisorChainFactory;

import java.lang.reflect.Method;
import java.util.List;

public class Test {

    public static void main(String[] args) throws Throwable {
        Test test = new Test();
        Class clazz = Test.class;
        Method testMethod = clazz.getDeclaredMethod("testMethod", String.class);

        /**
         * 调用链工厂
         *  spring aop包目前只有一个实现类(DefaultAdvisorChainFactory)
         *  目的就是将Advisor接口实现类转换成MethodInterceptor接口实现类,用于链式调用。
         *      转换过程中会判断Advisor子接口类型,这里是涉及到了PointcutAdvisor子接口。
         *      转换之前会涉及到Pointcut接口的判断,只有通过的才能进行转换。
         *      通过Advisor获取Advice,并转换成MethodInterceptor
         */
        AdvisorChainFactory advisorChainFactory = new TestDefaultAdvisorChainFactory();
        // 可认为是Advisor载体。实际在spring中,Advised实现类用于生成代理类用的。
        // 例如:ProxyFactory、ProxyFactoryBean
        Advised config = new TestDefaultAdvised();
        // 切点,拓展入口 实际干活的类
        Pointcut pointcut = new TestDefaultPointcut(new TestDefaultClassFilter(clazz), new TestDefaultMethodMatcher(true, true, true));
        // 通知,拓展入口 实际干活的类
        // 通知1
        Advice advice1 = (MethodBeforeAdvice) (method, args1, target) -> System.out.println("方法执行之前增强.... method name:" + method.getName() + ",target name:" + target.getClass().getName());
        // 通知2
        Advice advice2 = (MethodInterceptor) invocation -> {
            System.out.println("环绕通知.... 开始...");
            Object obj = invocation.proceed();
            System.out.println("环绕通知.... 结束...");
            return obj;
        };
        Advice advice3 = new TestThrowsAdvice();
        // Spring Aop中的增强器

        // 构建Advisor
        Advisor advisor1 = new TestDefaultPointcutAdvisor(pointcut, advice1);
        Advisor advisor2 = new TestDefaultPointcutAdvisor(pointcut, advice2);
        Advisor advisor3 = new TestDefaultPointcutAdvisor(pointcut, advice3);

        // 构造Advisor,添加
        config.addAdvisor(advisor1);
        config.addAdvisor(advisor2);
        config.addAdvisor(advisor3);

        // 通过Advisor获取拦截器链(MethodInterceptor or CopyInterceptorAndDynamicMethodMatcher)
        List<Object> interceptorsAndDynamicInterceptionAdvice = advisorChainFactory.getInterceptorsAndDynamicInterceptionAdvice(config, testMethod, clazz);

        /**
         * Joinpoint连接点的实现。
         *  Joinpoint ---> Object proceed();
         *   Invocation ---> Object[] getArguments();
         *    MethodInvocation ---> Method getMethod();
         *
         *  MethodInvocation用于MethodInterceptor接口的invoke方法形参。
         *  当调用目标方法的时候,会触发MethodInvocation实现类的proceed方法,从而触发一系列递归调用,最终调用目标方法,从而实现spring aop拦截调用。
         */
        TestDefaultMethodInvocation methodInvocation = new TestDefaultMethodInvocation(testMethod, new Object[]{"hello world"}, test, interceptorsAndDynamicInterceptionAdvice);

        // 执行调用链
        methodInvocation.proceed();
    }

    /**
     * 测试方法
     * @param params
     * @return
     */
    public String testMethod(String params) {
        System.out.println("执行testMethod方法... 参数:" + params);
        int i = 1 / 0;

        return params;
    }
}
